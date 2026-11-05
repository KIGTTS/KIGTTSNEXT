package com.kigtts.next.state

import android.net.Uri
import com.kigtts.next.cards.*
import com.kigtts.next.media.CameraPhotoEncoding
import com.kigtts.next.ui.cards.camera.PolaroidCameraCapture
import com.kigtts.next.ui.cards.camera.CameraMotionClip
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        scope.launch {
            try { for (pending in work) {
                try { save(pending) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { message("照片未能保存，原图已保留") }
                finally { queued.value = (queued.value - 1).coerceAtLeast(0) }
            } } finally { work.close() }
        }
    }
    internal fun enqueue(shots: List<PolaroidCameraCapture>, watermark: String = "none",
        encoding: CameraPhotoEncoding = CameraPhotoEncoding.JPEG, onSaved: (Uri) -> Unit = {}) {
        var reported = false
        val reportFallback: () -> Unit = {
            if (!reported) { reported = true; message("HEIF 未能保存，已改用 JPEG") }
        }
        shots.forEach { shot ->
            queued.value++
            val format = if (shot.motion != null) CameraPhotoEncoding.JPEG else shot.output?.encoding ?: encoding
            if (!work.trySend(Pending(shot, shot.output?.watermark ?: watermark, format, reportFallback, onSaved)).isSuccess) {
                queued.value--; message("照片未能加入保存队列，原图已保留")
            }
        }
    }
    private suspend fun save(pending: Pending) = withContext(Dispatchers.IO) {
        val shot = pending.shot
        val original = stickers.importResource(shot.original)
        val shown = if (shot.displayFile == shot.original) original else stickers.importResource(shot.displayFile)
        val card = PolaroidCard(title = "", imageId = shown.id, originalImageId = original.id,
            frameStyleId = PhotoFrameSpec(shown.width, shown.height, pending.watermark).id,
            photoStyle = shot.photoStyle?.persist(stickers)?.takeIf { it.hasPhotoTreatment() })
        val facts = PolaroidPhotoFacts.read(shot.original, true, shot.capturedAt)
        shot.motion?.let { saveMotion?.invoke(shown.id, it) }
        cards.systemAlbum.encodings.set(card.id, pending.encoding)
        cards.createPhoto(card, facts)
        // All inputs are now durable in the app's non-destructive library. Publish off the camera thread.
        val uri = cards.systemAlbum.save(card.id)
        saved.value = uri
        withContext(Dispatchers.Main.immediate) { pending.onSaved(uri) }
        if (pending.encoding == CameraPhotoEncoding.HEIF && !cards.systemAlbum.isHeif(card.id)) pending.reportFallback()
        shot.discard()
        // Location/weather can finish later without delaying the next exposure.
        scope.launch(Dispatchers.IO) {
            try {
                val enriched = PolaroidPhotoFacts.enrichCapture(stickers.context, facts, cards.designs.requiredFields())
                synchronized(stickers) { if (stickers.cards.value.any { it.id == card.id }) cards.designs.freeze(card.id, enriched) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Metadata is optional; the saved photograph remains usable. */ }
        }
    }
}
