package com.kigtts.next.identity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

internal fun writeProfileJpeg(source: File, target: File, cropAspect: Float? = null): Boolean {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, opts)
    val edge = maxOf(opts.outWidth, opts.outHeight)
    if (edge !in 1..32768) return false
    var sample = 1
    while (edge / sample > 2400) sample *= 2
    val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
    val crop = if (cropAspect == null) decoded else {
        val width = minOf(decoded.width, (decoded.height * cropAspect).toInt().coerceAtLeast(1))
        val height = minOf(decoded.height, (decoded.width / cropAspect).toInt().coerceAtLeast(1))
        Bitmap.createBitmap(decoded, (decoded.width - width) / 2, (decoded.height - height) / 2, width, height)
    }
    val scale = minOf(1f, 1600f / maxOf(crop.width, crop.height))
    val bitmap = if (scale == 1f) crop else Bitmap.createScaledBitmap(crop,
        (crop.width * scale).toInt().coerceAtLeast(1), (crop.height * scale).toInt().coerceAtLeast(1), true)
    return try { target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
    finally {
        if (bitmap !== crop) bitmap.recycle()
        if (crop !== decoded) crop.recycle()
        decoded.recycle()
    }
}
