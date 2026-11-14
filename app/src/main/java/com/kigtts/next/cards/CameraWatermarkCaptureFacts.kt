package com.kigtts.next.cards

import android.content.Context
import java.io.File
import kotlinx.coroutines.withTimeoutOrNull

/** Do not wait for dialog responses in the photo queue. */
internal suspend fun cameraWatermarkCaptureFacts(context: Context, source: File, takenAt: Long, watermark: String): FrozenPhotoFacts {
    val settings = PhotoServiceSettings(context)
    val now = System.currentTimeMillis()
    val capture = settings.captureFacts(PolaroidPhotoFacts.read(source, true, takenAt), now)
    val fields = CameraWatermarkStyle.decode(watermark)?.let(::watermarkRequestedFields).orEmpty()
    if (!settings.recordCapture || fields.isEmpty()) return capture
    return withTimeoutOrNull(5_500) {
        PolaroidPhotoFacts.enrich(context, capture, fields, AndroidPhotoFactsAccess(context, interactive = false), now)
    } ?: capture
}
