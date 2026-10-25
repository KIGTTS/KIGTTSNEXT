package com.kigtts.next.theme

import android.content.Context
import android.graphics.*
import hct.Hct
import java.io.File
import kotlin.math.*

/** Square master image: both device orientations share the same crop and center. */
data class BackgroundCrop(val x: Float = .5f, val y: Float = .5f, val zoom: Float = 1f) {
    fun bounds(width: Int, height: Int): RectF {
        val side = min(width, height) / zoom.coerceIn(1f, 8f)
        val cx = (x * width).coerceIn(side / 2, width - side / 2)
        val cy = (y * height).coerceIn(side / 2, height - side / 2)
        return RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
    }
    fun move(width: Int, height: Int, viewport: Float, panX: Float, panY: Float,
        scale: Float, focusX: Float = viewport / 2, focusY: Float = viewport / 2): BackgroundCrop {
        if (viewport <= 0) return this
        val before = bounds(width, height)
        val nextZoom = (zoom * scale).coerceIn(1f, 8f)
        val side = min(width, height) / nextZoom
        val cx = before.centerX() + (focusX / viewport - .5f) * (before.width() - side) - panX * side / viewport
        val cy = before.centerY() + (focusY / viewport - .5f) * (before.height() - side) - panY * side / viewport
        return BackgroundCrop(cx.coerceIn(side / 2, width - side / 2) / width,
            cy.coerceIn(side / 2, height - side / 2) / height, nextZoom)
    }
}

internal fun cropBackground(source: Bitmap, crop: BackgroundCrop): Bitmap {
    val bounds = crop.bounds(source.width, source.height)
    val side = min(2048, bounds.width().roundToInt()).coerceAtLeast(1)
    return Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also { output ->
        Canvas(output).apply { scale(side / bounds.width(), side / bounds.height()); translate(-bounds.left, -bounds.top)
            drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
    }
}

internal fun backgroundThemeSeed(bitmap: Bitmap): Long {
    val sample = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
    try {
        val pixels = IntArray(4096).also { sample.getPixels(it, 0, 64, 0, 0, 64, 64) }
        val bins = pixels.asSequence().groupingBy { (((it shr 20) and 15) shl 8) or (((it shr 12) and 15) shl 4) or ((it shr 4) and 15) }.eachCount()
        val winner = bins.maxByOrNull { (rgb, count) ->
            val argb = Color.rgb((rgb shr 8 and 15) * 17, (rgb shr 4 and 15) * 17, (rgb and 15) * 17)
            val hct = Hct.fromInt(argb)
            count * (1.0 + min(hct.chroma, 48.0) / 24.0) * if (hct.tone in 12.0..90.0) 1.0 else .3
        }?.key ?: 0x066
        return Color.rgb((winner shr 8 and 15) * 17, (winner shr 4 and 15) * 17, (winner and 15) * 17).toLong() and 0xffffffffL
    } finally { if (sample !== bitmap) sample.recycle() }
}

/** A versioned disk cache; expensive work only happens on import or cache miss, on Dispatchers.IO. */
internal class BackgroundBlurCache(context: Context) {
    private val assets = AppearanceAssets(context)
    private val root = File(context.cacheDir, "appearance-blur-v1").apply { mkdirs() }
    fun file(key: String?): File? {
        val source = assets.file(key) ?: return null
        val output = File(root, source.name)
        if (output.length() > 0) return output
        synchronized(lock) {
            if (output.length() > 0) return output
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, options)
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (max(options.outWidth, options.outHeight) / options.inSampleSize > 384) options.inSampleSize *= 2
            val bitmap = BitmapFactory.decodeFile(source.path, options) ?: return null
            val blurred = try { gaussianBlur(bitmap, 9f) } finally { bitmap.recycle() }
            val temp = File(root, source.name + ".tmp")
            try { temp.outputStream().use { check(blurred.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                check(temp.renameTo(output)); return output
            } finally { blurred.recycle(); temp.delete() }
        }
    }
    companion object { private val lock = Any() }
}

internal fun gaussianBlur(source: Bitmap, sigma: Float): Bitmap {
    val w = source.width; val h = source.height
    val radius = ceil(sigma.coerceAtLeast(.1f) * 3).toInt()
    val kernel = FloatArray(radius * 2 + 1) { exp(-((it - radius).toFloat().pow(2)) / (2 * sigma.coerceAtLeast(.1f).pow(2))) }
    val total = kernel.sum(); for (i in kernel.indices) kernel[i] /= total
    var pixels = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
    var next = IntArray(w * h)
    repeat(2) { pass ->
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0f; var g = 0f; var b = 0f
            for (k in -radius..radius) {
                val color = pixels[(if (pass == 0) y else (y + k).coerceIn(0, h - 1)) * w +
                    if (pass == 0) (x + k).coerceIn(0, w - 1) else x]
                val weight = kernel[k + radius]; r += Color.red(color) * weight; g += Color.green(color) * weight; b += Color.blue(color) * weight
            }
            next[y * w + x] = Color.rgb(r.roundToInt().coerceIn(0, 255), g.roundToInt().coerceIn(0, 255), b.roundToInt().coerceIn(0, 255))
        }
        val swap = pixels; pixels = next; next = swap
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}
