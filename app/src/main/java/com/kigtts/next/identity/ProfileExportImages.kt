package com.kigtts.next.identity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

internal fun writeProfileJpeg(source: File, target: File): Boolean {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, opts)
    val edge = maxOf(opts.outWidth, opts.outHeight)
    if (edge !in 1..32768) return false
    var sample = 1
    while (edge / sample > 2400) sample *= 2
    val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
    val scale = minOf(1f, 1600f / maxOf(decoded.width, decoded.height))
    val bitmap = if (scale == 1f) decoded else Bitmap.createScaledBitmap(decoded,
        (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
    return try { target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
    finally { if (bitmap !== decoded) bitmap.recycle(); decoded.recycle() }
}
