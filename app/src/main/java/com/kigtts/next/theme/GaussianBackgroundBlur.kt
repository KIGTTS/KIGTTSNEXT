package com.kigtts.next.theme

import android.graphics.Bitmap
import kotlin.math.*

/** Linear-light float32 in both passes; only the final cached image is quantized to 8 bits. */
internal fun gaussianBlur(source: Bitmap, sigma: Float): Bitmap {
    require(sigma.isFinite() && sigma > 0)
    val width = source.width; val height = source.height
    val radius = ceil(sigma.coerceAtLeast(.1f) * 3).toInt()
    val kernel = FloatArray(2 * radius + 1) { exp(-(it - radius).toFloat().pow(2) / (2 * sigma.coerceAtLeast(.1f).pow(2))) }
    val sum = kernel.sum(); kernel.indices.forEach { kernel[it] /= sum }
    val input = IntArray(width * height).also { source.getPixels(it, 0, width, 0, 0, width, height) }
    val decode = FloatArray(256) { value -> val c = value / 255f; if (c <= .04045f) c / 12.92f else ((c + .055f) / 1.055f).pow(2.4f) }
    var pixels = FloatArray(input.size * 4)
    var next = FloatArray(pixels.size)
    input.forEachIndexed { i, color ->
        val alpha = (color ushr 24) / 255f
        pixels[i * 4] = decode[color shr 16 and 255] * alpha
        pixels[i * 4 + 1] = decode[color shr 8 and 255] * alpha
        pixels[i * 4 + 2] = decode[color and 255] * alpha
        pixels[i * 4 + 3] = alpha
    }
    repeat(2) { pass ->
        for (y in 0 until height) for (x in 0 until width) {
            val target = (y * width + x) * 4
            for (k in -radius..radius) {
                val from = ((if (pass == 0) y else (y + k).coerceIn(0, height - 1)) * width +
                    if (pass == 0) (x + k).coerceIn(0, width - 1) else x) * 4
                val weight = kernel[k + radius]
                for (channel in 0..3) next[target + channel] += pixels[from + channel] * weight
            }
        }
        val swap = pixels; pixels = next; next = swap
        if (pass == 0) next.fill(0f)
    }
    for (index in input.indices) {
        val alpha = pixels[index * 4 + 3].coerceIn(0f, 1f)
        // Shared luminance dither avoids colored speckles; a stable pixel hash never shimmers.
        val noise = backgroundDither(index)
        fun channel(offset: Int): Int {
            val linear = if (alpha <= 1e-8f) 0f else (pixels[index * 4 + offset] / alpha).coerceIn(0f, 1f)
            val srgb = if (linear <= .0031308f) linear * 12.92f else 1.055f * linear.pow(1 / 2.4f) - .055f
            return (srgb * 255 + noise).roundToInt().coerceIn(0, 255)
        }
        input[index] = ((alpha * 255).roundToInt() shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
    }
    return Bitmap.createBitmap(input, width, height, Bitmap.Config.ARGB_8888)
}

private fun backgroundDither(index: Int): Float {
    fun uniform(seed: Int): Float {
        var value = seed
        value = (value xor (value ushr 16)) * -2048144789
        value = (value xor (value ushr 13)) * -1028477387
        value = value xor (value ushr 16)
        return (value ushr 8) / 16777216f
    }
    return uniform(index xor 0x3c6ef372) + uniform(index xor 0x1b873593) - 1f
}
