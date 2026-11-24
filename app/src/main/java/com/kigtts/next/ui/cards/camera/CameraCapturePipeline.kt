package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageCapture
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Capture never renders the recipe into the JPEG. Preview and export apply it independently. */
internal suspend fun capturePolaroidPhoto(context: Context, capture: ImageCapture, front: Boolean,
    format: PolaroidFormat, filter: PhotoFilter, message: (String) -> Unit, strength: Float = 1f,
    lut: com.kigtts.next.media.color.CubeLut? = null,
    effects: com.kigtts.next.media.color.PhotoEffects = com.kigtts.next.media.color.PhotoEffects.NONE,
    exposureStarted: () -> Unit = {}): PolaroidCameraCapture {
    val directory = File(context.cacheDir, "polaroid-camera")
    val id = UUID.randomUUID().toString()
    val original = File(directory, "$id-original.jpg")
    val started = SystemClock.elapsedRealtime()
    val capturedAt = System.currentTimeMillis()
    try {
        directory.mkdirs()
        capture.captureJpeg(context, original, front, exposureStarted)
        val captured = SystemClock.elapsedRealtime()
        Log.d(CAMERA_LOG_TAG, "JPEG ready after ${captured - started}ms,bytes=${original.length()},resolution=${capture.resolutionInfo?.resolution}")
        currentCoroutineContext().ensureActive()
        Log.d(CAMERA_LOG_TAG, "Photo handoff after ${SystemClock.elapsedRealtime() - started}ms,filter=$filter,strength=$strength,processing=${SystemClock.elapsedRealtime() - captured}ms")
        return PolaroidCameraCapture(original, null, format, filter, capturedAt = capturedAt,
            photoStyle = com.kigtts.next.cards.CapturedPhotoStyle(filter, strength, lut, effects))
    } catch (failure: Throwable) {
        original.delete()
        throw failure
    }
}

internal suspend fun processBurstPhoto(photo: PolaroidCameraCapture, filter: PhotoFilter, strength: Float,
    lut: com.kigtts.next.media.color.CubeLut?, effects: com.kigtts.next.media.color.PhotoEffects,
    message: (String) -> Unit): PolaroidCameraCapture {
    currentCoroutineContext().ensureActive()
    return PolaroidCameraCapture(photo.original, null, photo.format, filter, photo.motion, photo.capturedAt,
        com.kigtts.next.cards.CapturedPhotoStyle(filter, strength, lut, effects), expression = photo.expression)
}
