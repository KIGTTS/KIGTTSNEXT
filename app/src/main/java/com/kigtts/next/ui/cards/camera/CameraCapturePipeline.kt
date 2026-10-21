package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageCapture
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Produces an owned high-resolution photo, preserving its original JPEG until the host imports both copies. */
internal suspend fun capturePolaroidPhoto(context: Context, capture: ImageCapture, front: Boolean,
    format: PolaroidFormat, filter: PhotoFilter, message: (String) -> Unit, strength: Float = 1f,
    lut: com.kigtts.next.media.color.CubeLut? = null,
    effects: com.kigtts.next.media.color.PhotoEffects = com.kigtts.next.media.color.PhotoEffects.NONE,
    exposureStarted: () -> Unit = {}): PolaroidCameraCapture {
    val directory = File(context.cacheDir, "polaroid-camera")
    val id = UUID.randomUUID().toString()
    val original = File(directory, "$id-original.jpg"); val processed = File(directory, "$id-filtered.jpg")
    val started = SystemClock.elapsedRealtime()
    try {
        directory.mkdirs()
        capture.captureJpeg(context, original, front, exposureStarted)
        val captured = SystemClock.elapsedRealtime()
        Log.d(CAMERA_LOG_TAG, "JPEG ready after ${captured - started}ms,bytes=${original.length()},resolution=${capture.resolutionInfo?.resolution}")
        val filtered = try { withContext(Dispatchers.Default) { PhotoFilterWriter.write(original, processed, filter, strength, lut, effects) } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: OutOfMemoryError) { message("原图已保留，本次未应用滤镜"); null }
        catch (_: Exception) { message("原图已保留，本次未应用滤镜"); null }
        currentCoroutineContext().ensureActive()
        Log.d(CAMERA_LOG_TAG, "Photo handoff after ${SystemClock.elapsedRealtime() - started}ms,filter=$filter,strength=$strength,processing=${SystemClock.elapsedRealtime() - captured}ms")
        return PolaroidCameraCapture(original, filtered, format, if (filtered == null) PhotoFilter.ORIGINAL else filter)
    } catch (failure: Throwable) {
        original.delete(); processed.delete()
        throw failure
    }
}
