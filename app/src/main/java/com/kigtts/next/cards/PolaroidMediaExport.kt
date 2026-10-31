@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.cards

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
    val clip = File(directory, "composed.mp4"); val jpeg = File(directory, "cover.jpg")
    var overlay: Bitmap? = null
    try {
        val paper = withContext(Dispatchers.IO) { PolaroidRenderer.renderBitmap(stickers, card, options, transparentPhoto = true, maximumHeight = 1080) }
        overlay = paper
        val effect = CardMotionPaperEffect(card, stickers.frameStyle(card), paper, motion.colorMatrix, includeFrame, motion.lut, motion.lutStrength, motion.effects)
        transform(stickers, motion, effect, clip)
        return withContext(Dispatchers.IO) {
            val still = PolaroidRenderer.renderBitmap(stickers, card, options, maximumHeight = maximumHeight)
            try { jpeg.outputStream().use { check(still.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "无法生成实况封面" } } }
            finally { still.recycle() }
            val output = File(stickers.root, "exports/${card.id}-${UUID.randomUUID()}MP.jpg")
            MotionPhotoWriter.write(jpeg, clip, output, motion.shutterPositionMs * 1000)
        }
    } finally { overlay?.recycle(); withContext(NonCancellable + Dispatchers.IO) { directory.deleteRecursively() } }
}

private suspend fun transform(stickers: StickerRepository, source: CardMotionClip, effect: CardMotionPaperEffect, output: File) =
    withContext(Dispatchers.Main.immediate) {
        var current: Transformer? = null
        try { suspendCancellableCoroutine<Unit> { continuation ->
            val transformer = Transformer.Builder(stickers.context).setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (continuation.isActive) continuation.resume(Unit) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                }).build()
            current = transformer
            continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { transformer.cancel(); output.delete() } }
            try {
                transformer.start(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source.video)))
                    .setEffects(Effects(emptyList(), listOf(effect))).build(), output.absolutePath)
            } catch (failure: Exception) { transformer.cancel(); if (continuation.isActive) continuation.resumeWithException(failure) }
        } } finally { withContext(NonCancellable + Dispatchers.Main.immediate) { current?.cancel() } }
    }
