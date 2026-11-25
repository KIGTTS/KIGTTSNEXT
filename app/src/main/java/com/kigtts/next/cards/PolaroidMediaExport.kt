@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.cards

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import com.kigtts.next.media.MotionPhotoWriter
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun exportPolaroidStill(stickers: StickerRepository, card: PolaroidCard, includeFrame: Boolean = true,
    includeStickers: Boolean = true, maximumHeight: Int = 1500): File = PolaroidRenderer.export(stickers, card, PolaroidExportOptions(includeFrame, includeStickers), maximumHeight)

/** Both quick-text media and ordinary sharing use this one design-aware export path. */
suspend fun exportPolaroidMedia(stickers: StickerRepository, card: PolaroidCard, includeFrame: Boolean = true,
    includeStickers: Boolean = true, maximumHeight: Int = 1500): File {
    val motion = withContext(Dispatchers.IO) { CardMotionRepository(stickers).get(card.imageId)?.withPhotoStyle(card.photoStyle, stickers::imageFile) }
        ?: return withContext(Dispatchers.IO) { exportPolaroidStill(stickers, card, includeFrame, includeStickers, maximumHeight) }
    val options = PolaroidExportOptions(includeFrame, includeStickers)
    val directory = File(stickers.context.cacheDir, "cards/motion-" + UUID.randomUUID())
    check(withContext(Dispatchers.IO) { directory.mkdirs() }) { "无法准备实况导出" }
    val jpeg = File(directory, "cover.jpg")
    var overlay: Bitmap? = null
    try {
        val clip = if (canKeepEncodedMotion(card, motion)) {
            motion.video
        } else {
            val paper = withContext(Dispatchers.IO) { PolaroidRenderer.renderBitmap(stickers, card, options, transparentPhoto = true, maximumHeight = 1080) }
            overlay = paper
            val effect = CardMotionPaperEffect(card, stickers.frameStyle(card), paper, motion.colorMatrix, includeFrame, motion.lut, motion.lutStrength, motion.effects)
            File(directory, "composed.mp4").also { transform(stickers, motion, effect, it) }
        }
        return withContext(Dispatchers.IO) {
            val still = PolaroidRenderer.renderBitmap(stickers, card, options, maximumHeight = maximumHeight)
            try { jpeg.outputStream().use { check(still.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "无法生成实况封面" } } }
            finally { still.recycle() }
            val output = File(stickers.root, "exports/${card.id}-${UUID.randomUUID()}MP.jpg")
            MotionPhotoWriter.write(jpeg, clip, output, motion.shutterPositionMs * 1000)
        }
    } finally { overlay?.recycle(); withContext(NonCancellable + Dispatchers.IO) { directory.deleteRecursively() } }
}

/** The default camera cover and clip already share the same viewfinder crop. */
private suspend fun canKeepEncodedMotion(card: PolaroidCard, motion: CardMotionClip): Boolean {
    val frame = PhotoFrameSpec.from(card.frameStyleId) ?: return false
    val plain = frame.style == "none" && frame.crop == null && card.title.isEmpty() && card.description.isEmpty() &&
        card.layers.isEmpty() && card.photoZoom == 1.0 && card.photoX == .5 && card.photoY == .5 &&
        motion.colorMatrix.contentEquals(CardMotionRepository.identity()) && motion.lut == null &&
        motion.effects == com.kigtts.next.media.color.PhotoEffects.NONE
    return plain && withContext(Dispatchers.IO) {
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(motion.video.absolutePath)
            val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLongOrNull() ?: 0
            val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLongOrNull() ?: 0
            val rotated = motion.rotationDegrees % 180 != 0
            val clipWidth = if (rotated) height else width
            val clipHeight = if (rotated) width else height
            // Imported covers and clips can have different crops. Re-compose those clips as usual.
            clipWidth > 0 && clipHeight > 0 &&
                kotlin.math.abs(clipWidth * frame.height - clipHeight * frame.width) * 100 <= clipHeight * frame.width
        } catch (_: RuntimeException) { false }
        finally { metadata.release() }
    }
}

private suspend fun transform(stickers: StickerRepository, source: CardMotionClip, effect: CardMotionPaperEffect, output: File) {
    // Transformer must be accessed from one Looper. Keep its setup, callbacks and teardown off UI.
    val thread = HandlerThread("KigPhotoMotionExport").apply { start() }
    val handler = Handler(thread.looper)
    val dispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
            check(handler.post(block)) { "实况处理线程已关闭" }
        }
    }
    try { withContext(dispatcher) {
        var current: Transformer? = null
        try { suspendCancellableCoroutine<Unit> { continuation ->
            val transformer = Transformer.Builder(stickers.context).setLooper(thread.looper).setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (continuation.isActive) continuation.resume(Unit) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                }).build()
            current = transformer
            try {
                transformer.start(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source.video)))
                    .setEffects(Effects(emptyList(), listOf(effect))).build(), output.absolutePath)
            } catch (failure: Exception) { transformer.cancel(); if (continuation.isActive) continuation.resumeWithException(failure) }
        } } catch (unavailable: LinkageError) {
            throw IllegalStateException("Motion Photo video export unavailable", unavailable)
        } finally { current?.cancel() }
    } } finally { thread.quitSafely() }
}
