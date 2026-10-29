package com.kigtts.next.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

internal data class ResourceVector(val picture: android.graphics.Picture, val whiteLine: Boolean)

/** Keep SVG draw commands so avatar scaling never enlarges a cached bitmap. */
internal fun decodeResourceVector(file: File?): ResourceVector? = runCatching {
    if (file == null || !file.isFile || file.length() !in 1..512L * 1024) return null
    val prefix = file.inputStream().use { input ->
        val buffer = ByteArray(256); val count = input.read(buffer)
        String(buffer, 0, count.coerceAtLeast(0), Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
    }
    if (!prefix.startsWith("<svg") && !prefix.startsWith("<?xml") && !prefix.startsWith("<!--")) return null
    val picture = UiIcons.renderSvgPicture(file.readText(Charsets.UTF_8))
    val probe = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
    val white = try {
        android.graphics.Canvas(probe).apply { scale(32f / picture.width, 32f / picture.height); drawPicture(picture) }
        probe.isWhiteLineArtwork()
    } finally { probe.recycle() }
    ResourceVector(picture, white)
}.getOrNull()

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
