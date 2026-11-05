package com.kigtts.next.media

import android.graphics.Bitmap
import android.media.MediaCodecList
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.heifwriter.HeifWriter
import java.io.File
import java.io.IOException

internal enum class CameraPhotoEncoding(val label: String, val extension: String) {
    JPEG("JPEG", "jpg"), HEIF("HEIF", "heic");

    companion object {
        fun read(value: String?) = entries.firstOrNull { it.name == value } ?: JPEG
        val heifAvailable by lazy {
            Build.VERSION.SDK_INT >= 28 && runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
                    codec.isEncoder && codec.supportedTypes.any { it.equals("video/hevc", true) || it.equals("image/vnd.android.heic", true) }
                }
            }.getOrDefault(false)
        }
    }
}

/** Files are staged off the UI thread; MediaStore never sees a failed or partial encode. */
internal fun encodeCameraPhoto(bitmap: Bitmap, file: File, format: CameraPhotoEncoding) {
    if (format == CameraPhotoEncoding.HEIF) {
        if (Build.VERSION.SDK_INT < 28) throw IOException("设备不支持保存 HEIF")
        encodeHeif(bitmap, file)
    } else file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 98, it)) }
    check(file.length() > 0) { "照片编码失败" }
}

@RequiresApi(28)
private fun encodeHeif(bitmap: Bitmap, file: File) {
    HeifWriter.Builder(file.absolutePath, bitmap.width, bitmap.height, HeifWriter.INPUT_MODE_BITMAP)
        .setQuality(96).setGridEnabled(true).build().use { writer ->
            writer.start()
            writer.addBitmap(bitmap)
            writer.stop(20_000)
        }
}
