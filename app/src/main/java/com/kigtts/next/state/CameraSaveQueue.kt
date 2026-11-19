package com.kigtts.next.state

import com.kigtts.next.i18n.uiCopy

import android.net.Uri
import android.util.Log
import com.kigtts.next.cards.*
import com.kigtts.next.media.*
import com.kigtts.next.ui.cards.camera.PolaroidCameraCapture
import com.kigtts.next.ui.cards.camera.CameraMotionClip
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Camera ownership ends at enqueue, not after metadata, rendering or MediaStore has completed. */
class CameraSaveQueue internal constructor(private val cards: CardRepository, private val stickers: StickerRepository,
    private val scope: CoroutineScope, private val message: (String) -> Unit,
    private val saveMotion: ((String, CameraMotionClip) -> Unit)? = null) {
    private data class Pending(val shot: PolaroidCameraCapture, val watermark: String, val encoding: CameraPhotoEncoding,
        val reportFallback: () -> Unit, val onSaved: (Uri) -> Unit)
    private val work = Channel<Pending>(Channel.UNLIMITED)
    private val queued = MutableStateFlow(0); val count = queued.asStateFlow()
    private val saved = MutableStateFlow<Uri?>(null); val latest = saved.asStateFlow()
    init {
        // One IO worker owns expensive still rendering and publication. Capture can continue
        // immediately; serialize work to avoid multiple full-size bitmaps competing for memory.
        scope.launch(Dispatchers.IO.limitedParallelism(1)) {
            try { for (pending in work) {
                try { save(pending) }
                catch (cancel: CancellationException) { throw cancel }
                catch (failure: Exception) {
                    Log.w("KigCameraSave", "Could not publish photo", failure)
                    message(stickers.context.uiCopy("camera.photo_save_failed_original"))
                }
                catch (failure: OutOfMemoryError) {
                    Log.w("KigCameraSave", "Insufficient memory for photo processing", failure)
                    message(stickers.context.uiCopy("camera.photo_save_failed_original"))
                }
                finally { queued.update { (it - 1).coerceAtLeast(0) } }
            } } finally { work.close() }
        }
    }
    internal fun enqueue(shots: List<PolaroidCameraCapture>, watermark: String = "none",
        encoding: CameraPhotoEncoding = CameraPhotoEncoding.JPEG, onSaved: (Uri) -> Unit = {}) {
        var reported = false
        val reportFallback: () -> Unit = {
            if (!reported) { reported = true; message(stickers.context.uiCopy("camera.heif_fallback")) }
        }
        shots.forEach { shot ->
            queued.update { it + 1 }
            val format = if (shot.motion != null) CameraPhotoEncoding.JPEG else shot.output?.encoding ?: encoding
            if (!work.trySend(Pending(shot, shot.output?.watermark ?: watermark, format, reportFallback, onSaved)).isSuccess) {
                queued.update { (it - 1).coerceAtLeast(0) }; message(stickers.context.uiCopy("camera.enqueue_failed_original"))
            }
        }
    }
    private suspend fun save(pending: Pending) {
        val shot = pending.shot
        if (pending.watermark == "none" && pending.encoding == CameraPhotoEncoding.JPEG && shot.motion == null &&
            shot.displayFile == shot.original && shot.photoStyle?.requiresRendering() != true) {
            check(requestGalleryWrite(stickers.context)) { "未允许保存到系统相册" }
            val uri = GalleryImages(stickers.context).publish(shot.original, "相机", shot.capturedAt)
            shot.discard()
            notifySaved(pending, uri)
            return
        }
        val original = stickers.importResource(shot.original)
        val shown = if (shot.displayFile == shot.original) original else stickers.importResource(shot.displayFile)
        val card = PolaroidCard(title = "", imageId = shown.id, originalImageId = original.id,
            frameStyleId = PhotoFrameSpec(shown.width, shown.height, pending.watermark).id,
            photoStyle = shot.photoStyle?.persist(stickers)?.takeIf { it.hasPhotoTreatment() })
        val facts = cameraWatermarkCaptureFacts(stickers.context, shot.original, shot.capturedAt, pending.watermark)
        shot.motion?.let { saveMotion?.invoke(shown.id, it) }
        cards.systemAlbum.encodings.set(card.id, pending.encoding)
        cards.documents.hold(card.id)
        cards.createCameraPhoto(card, facts)
        // Publish the self-contained photo first; never remove the source before MediaStore commits.
        val uri = cards.systemAlbum.save(card.id, embedOriginal = true)
        val usedHeif = cards.systemAlbum.isHeif(card.id)
        shot.discard()
        runCatching {
            cards.documents.releasePublished(card)
            if (shot.motion != null) CardMotionRepository(stickers).prune()
        }.onFailure { Log.w("KigCameraSave", "Could not remove the published photo's scratch files", it) }
        notifySaved(pending, uri)
        if (pending.encoding == CameraPhotoEncoding.HEIF && !usedHeif) pending.reportFallback()
    }

    private suspend fun notifySaved(pending: Pending, uri: Uri) {
        saved.value = uri
        withContext(Dispatchers.Main.immediate) { pending.onSaved(uri) }
    }
}
