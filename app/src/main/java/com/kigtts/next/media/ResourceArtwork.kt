package com.kigtts.next.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Catalog artwork can have hash-addressed filenames without a format extension. */
internal fun decodeResourceArtwork(file: File?, size: Int = 256): Bitmap? = runCatching {
    if (file == null || !file.isFile || file.length() !in 1..8L * 1024 * 1024) return null
    val prefix = file.inputStream().use { input ->
        val buffer = ByteArray(256); val count = input.read(buffer)
        String(buffer, 0, count.coerceAtLeast(0), Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
    }
    if (prefix.startsWith("<svg") || prefix.startsWith("<?xml") || prefix.startsWith("<!--")) {
        if (file.length() > 512 * 1024) return null
        UiIcons.renderSvg(file.readText(Charsets.UTF_8), size)
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 32_000_000) return null
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / size).coerceAtLeast(1)
        })
    }
}.getOrNull()

/** Transparent white line artwork follows the same theme tint as built-in icons. */
internal fun Bitmap.isWhiteLineArtwork(): Boolean {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    var transparent = false; var visible = false
    for (pixel in pixels) {
        if (android.graphics.Color.alpha(pixel) == 0) transparent = true
        else {
            visible = true
            if (android.graphics.Color.red(pixel) < 250 || android.graphics.Color.green(pixel) < 250 || android.graphics.Color.blue(pixel) < 250) return false
        }
    }
    return transparent && visible
}
