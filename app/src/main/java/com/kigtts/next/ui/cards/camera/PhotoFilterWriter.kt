package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** Process the original resolution in small pixel strips using one mutable bitmap, not two full copies. */
internal object PhotoFilterWriter {
    const val MAX_PIXELS = 80_000_000L
    suspend fun write(original: File, output: File, filter: PhotoFilter, strength: Float = 1f,
        lut: com.kigtts.next.media.color.CubeLut? = null): File? {
        if (filter == PhotoFilter.ORIGINAL || strength <= 0f) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        original.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= MAX_PIXELS) { "照片尺寸超出支持范围" }
        val image = requireNotNull(original.inputStream().use { stream -> BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
            inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888
        }) }) { "照片暂时无法读取" }
        val stage = File(output.parentFile, ".${output.name}.tmp")
        try {
            applyPhotoFilterBands(image, filter, strength, lut)
            currentCoroutineContext().ensureActive()
            stage.outputStream().use { require(image.compress(Bitmap.CompressFormat.JPEG, 98, it)) { "照片暂时无法保存" } }
            val sourceExif = original.inputStream().use { ExifInterface(it) }
            ExifInterface(stage).apply {
                listOf(ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_WHITE_BALANCE).forEach { tag ->
                    sourceExif.getAttribute(tag)?.let { setAttribute(tag, it) }
                }
                saveAttributes()
            }
            currentCoroutineContext().ensureActive()
            java.nio.file.Files.move(stage.toPath(), output.toPath())
            return output
        } finally { image.recycle(); stage.delete() }
    }
}
