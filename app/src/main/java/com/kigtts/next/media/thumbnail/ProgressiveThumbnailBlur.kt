package com.kigtts.next.media.thumbnail

import android.graphics.Bitmap
import kotlin.math.*

/** Continuously varying Gaussian radius, premultiplied alpha; no blur-level boundaries. */
internal object ProgressiveThumbnailBlur {
    private fun smooth(value: Float) = value.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    fun apply(source: Bitmap, check: () -> Unit): Bitmap {
        val factor = min(1f, 128f / max(source.width, source.height))
        val w = (source.width * factor).roundToInt().coerceAtLeast(1)
        val h = (source.height * factor).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val pixels = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        if (small !== source) small.recycle()
        val kernels = List(w) { x ->
            val sigma = 9f * (1 - smooth(x.toFloat() / w / .8f))
            if (sigma < .05f) floatArrayOf(1f) else {
                val radius = ceil(sigma * 3).toInt()
                FloatArray(radius * 2 + 1) { k -> exp(-(k - radius).toFloat().pow(2) / (2 * sigma * sigma)) }
                    .also { val sum = it.sum(); it.indices.forEach { i -> it[i] /= sum } }
            }
        }
        val horizontal = FloatArray(pixels.size * 4)
        for (y in 0 until h) {
            check()
            for (x in 0 until w) {
                val kernel = kernels[x]; val radius = kernel.size / 2; val offset = (y * w + x) * 4
                kernel.forEachIndexed { i, weight ->
                    val color = pixels[y * w + (x + i - radius).coerceIn(0, w - 1)]
                    val alpha = (color ushr 24).toFloat()
                    horizontal[offset + 3] += alpha * weight
                    for (channel in 0..2) horizontal[offset + channel] += ((color ushr (channel * 8)) and 255) * (alpha / 255) * weight
                }
            }
        }
        val blurred = IntArray(pixels.size)
        for (y in 0 until h) {
            check()
            for (x in 0 until w) {
                val kernel = kernels[x]; val radius = kernel.size / 2; val values = FloatArray(4)
                kernel.forEachIndexed { i, weight ->
                    val offset = ((y + i - radius).coerceIn(0, h - 1) * w + x) * 4
                    for (channel in 0..3) values[channel] += horizontal[offset + channel] * weight
                }
                var color = values[3].roundToInt().coerceIn(0, 255) shl 24
                if (values[3] > 0) for (channel in 0..2) color = color or
                    ((values[channel] * 255 / values[3]).roundToInt().coerceIn(0, 255) shl (channel * 8))
                blurred[y * w + x] = color
            }
        }
        val bitmap = Bitmap.createBitmap(blurred, w, h, Bitmap.Config.ARGB_8888)
        val scaled = Bitmap.createScaledBitmap(bitmap, source.width, source.height, true)
        if (scaled !== bitmap) bitmap.recycle()
        val soft = IntArray(source.width * source.height).also { scaled.getPixels(it, 0, source.width, 0, 0, source.width, source.height) }
        scaled.recycle()
        val sharp = IntArray(soft.size).also { source.getPixels(it, 0, source.width, 0, 0, source.width, source.height) }
        for (y in 0 until source.height) {
            check()
            for (x in 0 until source.width) {
                val i = y * source.width + x
                val t = smooth((x.toFloat() / source.width - .42f) / .38f)
                val a = (soft[i] ushr 24) * (1 - t); val b = (sharp[i] ushr 24) * t; val alpha = a + b
                var color = alpha.roundToInt().coerceIn(0, 255) shl 24
                if (alpha > 0) for (shift in intArrayOf(16, 8, 0)) color = color or
                    (((((soft[i] ushr shift) and 255) * a + ((sharp[i] ushr shift) and 255) * b) / alpha).roundToInt().coerceIn(0, 255) shl shift)
                soft[i] = color
            }
        }
        return Bitmap.createBitmap(soft, source.width, source.height, Bitmap.Config.ARGB_8888)
    }
}
