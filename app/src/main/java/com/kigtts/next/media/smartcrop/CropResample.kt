package com.kigtts.next.media.smartcrop

import kotlin.math.*

/** Bilinear, premultiplied-alpha prescaling. Canvas interpolation can differ by browser. */
internal object CropResample {
    fun resize(input: CropImage, width: Int, height: Int): CropImage {
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val sx = ((x + .5) * input.width / width - .5).coerceIn(0.0, input.width - 1.0)
            val sy = ((y + .5) * input.height / height - .5).coerceIn(0.0, input.height - 1.0)
            val x0 = sx.toInt(); val y0 = sy.toInt(); val dx = sx - x0; val dy = sy - y0
            val colors = intArrayOf(input.pixels[y0 * input.width + x0],
                input.pixels[y0 * input.width + min(x0 + 1, input.width - 1)],
                input.pixels[min(y0 + 1, input.height - 1) * input.width + x0],
                input.pixels[min(y0 + 1, input.height - 1) * input.width + min(x0 + 1, input.width - 1)])
            val weights = doubleArrayOf((1 - dx) * (1 - dy), dx * (1 - dy), (1 - dx) * dy, dx * dy)
            val alpha = colors.indices.sumOf { (colors[it] ushr 24) * weights[it] }
            var color = byte(alpha) shl 24
            if (alpha > 0) for (shift in intArrayOf(16, 8, 0)) {
                val channel = colors.indices.sumOf { ((colors[it] ushr shift) and 255) * (colors[it] ushr 24) * weights[it] } / alpha
                color = color or (byte(channel) shl shift)
            }
            pixels[y * width + x] = color
        }
        return CropImage(width, height, pixels)
    }
    /** ECMAScript Uint8ClampedArray conversion: nearest integer, ties to even. */
    fun byte(value: Double) = Math.rint(value.coerceIn(0.0, 255.0)).toInt()
}
