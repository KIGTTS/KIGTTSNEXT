@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun saveCameraVideo(context: Context, original: File, timeLapse: Boolean): Uri {
    check(requestGalleryWrite(context)) { "未允许保存到系统相册" }
    val accelerated = if (timeLapse) File(original.parentFile, "${UUID.randomUUID()}-timelapse.mp4") else null
    try {
        if (accelerated != null) accelerateVideo(context, original, accelerated)
        return withContext(Dispatchers.IO) {
            val input = accelerated ?: original
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "KIGTTS-${UUID.randomUUID()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
                if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/KIGTTS"); put(MediaStore.Video.Media.IS_PENDING, 1) }
                else {
                    @Suppress("DEPRECATION") val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "KIGTTS").apply { mkdirs() }
                    put(MediaStore.Video.Media.DATA, File(directory, valuesName()).path)
                }
            }
            val resolver = context.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
            try {
                requireNotNull(resolver.openOutputStream(uri, "w")).use { output -> input.inputStream().use { it.copyTo(output) } }
                if (Build.VERSION.SDK_INT >= 29) resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                original.delete(); uri
            } catch (failure: Exception) { resolver.delete(uri, null, null); throw failure }
        }
    } finally { accelerated?.delete() }
}
private fun valuesName() = "KIGTTS-${UUID.randomUUID()}.mp4"

private suspend fun accelerateVideo(context: Context, source: File, output: File) = withContext(Dispatchers.Main.immediate) {
    var transformer: Transformer? = null
    try { suspendCancellableCoroutine<Unit> { continuation ->
        val worker = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (continuation.isActive) continuation.resume(Unit) }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { if (continuation.isActive) continuation.resumeWithException(exportException) }
        }).build()
        transformer = worker
        worker.start(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source))).setRemoveAudio(true).setFrameRate(30)
            .setEffects(Effects(emptyList(), listOf(SpeedChangeEffect(10f)))).build(), output.path)
    } } finally { withContext(NonCancellable + Dispatchers.Main.immediate) { transformer?.cancel() } }
}
