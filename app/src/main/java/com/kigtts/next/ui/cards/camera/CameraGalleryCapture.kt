package com.kigtts.next.ui.cards.camera

import com.kigtts.next.i18n.uiCopy

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.kigtts.next.media.MotionPhoto
import com.kigtts.next.cards.openPhotoSource
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal data class CameraGalleryResult(val photo: PolaroidCameraCapture, val warning: String? = null)

/** Copy the selected grant once, preserving its encoded still/EXIF before extracting Android's embedded video. */
internal suspend fun cameraGalleryCapture(context: Context, uri: Uri, format: PolaroidFormat): CameraGalleryResult = withContext(Dispatchers.IO) {
    require(uri.scheme in setOf("content", "file")) { context.uiCopy("camera.gallery_local_only") }
    val directory = File(context.cacheDir, "polaroid-camera/gallery").apply { check(mkdirs() || isDirectory) }
    val key = UUID.randomUUID().toString()
    val raw = File(directory, "$key-selected")
    val still = File(directory, "$key-original.jpg")
    val video = File(directory, "$key-motion.mp4")
    try {
        openPhotoSource(context, uri).use { input ->
            raw.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var count = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val size = input.read(buffer); if (size < 0) break
                    count += size; require(count <= 128L * 1024 * 1024) { context.uiCopy("camera.gallery_size") }
                    output.write(buffer, 0, size)
                }
                require(count > 0) { context.uiCopy("camera.gallery_empty") }
            }
        }
        val embedded = MotionPhoto.locate(raw)
        val stillBytes = embedded?.offset ?: raw.length()
        require(stillBytes in 1..32L * 1024 * 1024) { context.uiCopy("camera.gallery_still_size") }
        raw.inputStream().use { input -> still.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024); var remaining = stillBytes
            while (remaining > 0) {
                coroutineContext.ensureActive()
                val size = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                check(size > 0); output.write(buffer, 0, size); remaining -= size
            }
        } }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        still.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768 && bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000) { context.uiCopy("camera.gallery_invalid") }
        var warning: String? = null
        val motion = if (embedded != null) try {
            check(MotionPhoto.extract(raw, video))
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(video.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                require(duration in 1..30000 && rotation in setOf(0, 90, 180, 270) && width > 0)
                val header = raw.inputStream().use { input ->
                    val bytes = ByteArray(minOf(raw.length(), 4L * 1024 * 1024).toInt()); var count = 0
                    while (count < bytes.size) { val size = input.read(bytes, count, bytes.size - count); if (size < 0) break; count += size }
                    String(bytes, 0, count, Charsets.ISO_8859_1)
                }
                val timestamp = cameraGalleryShutterPosition(header, duration)
                CameraMotionClip(video, duration, rotation, timestamp, PhotoFilters.coefficients(PhotoFilter.ORIGINAL))
            } finally { retriever.release() }
        } catch (_: Exception) { video.delete(); warning = context.uiCopy("camera.gallery_motion_warning"); null } else null
        coroutineContext.ensureActive()
        CameraGalleryResult(PolaroidCameraCapture(still, null, format, PhotoFilter.ORIGINAL, motion), warning)
    } catch (failure: Throwable) { still.delete(); video.delete(); throw failure }
    finally { raw.delete() }
}

/** Android permits absent/-1 timestamps; choose the clip midpoint in that case, never its first frame. */
internal fun cameraGalleryShutterPosition(header: String, durationMs: Long): Long {
    require(durationMs > 0)
    val name = "(?:MotionPhotoPresentationTimestampUs|MicroVideoPresentationTimestampUs)"
    val attribute = Regex("$name\\s*=\\s*[\"'](-?\\d+)[\"']").find(header)?.groupValues?.get(1)
    val element = Regex("<(?:[A-Za-z_][\\w.-]*:)?$name(?:\\s[^>]*)?>\\s*(-?\\d+)\\s*</").find(header)?.groupValues?.get(1)
    return (attribute ?: element)?.toLongOrNull()?.takeIf { it >= 0 }?.div(1000)?.coerceIn(0, durationMs) ?: durationMs / 2
}
