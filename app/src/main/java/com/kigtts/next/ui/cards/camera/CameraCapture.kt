package com.kigtts.next.ui.cards.camera

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun ImageCapture.captureJpeg(context: Context, file: File, front: Boolean): File = suspendCancellableCoroutine { continuation ->
    val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = front }
    val options = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
    try {
        takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                if (continuation.isActive) continuation.resume(file) else file.delete()
            }
            override fun onError(exception: ImageCaptureException) {
                file.delete()
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
        })
    } catch (error: Exception) { file.delete(); if (continuation.isActive) continuation.resumeWithException(error) }
}
