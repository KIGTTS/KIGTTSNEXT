package com.kigtts.next.media

import android.graphics.*
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Software bitmap, bounded decoded dimensions and EXIF/HEIF orientation; call off the main thread. */
object MediaBitmapDecoder {
    fun decode(file: File, maximum: Int): Bitmap? {
        require(maximum > 0)
        if (Build.VERSION.SDK_INT >= 28) runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val factor = maxOf(info.size.width, info.size.height).toFloat() / maximum
                if (factor > 1) decoder.setTargetSize((info.size.width / factor).toInt().coerceAtLeast(1), (info.size.height / factor).toInt().coerceAtLeast(1))
            }
        }.getOrNull()?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maximum) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val exif = runCatching { ExifInterface(file) }.getOrNull()
        val matrix = Matrix().apply { exif?.let { if (it.isFlipped) postScale(-1f, 1f); postRotate(it.rotationDegrees.toFloat()) } }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }
}
