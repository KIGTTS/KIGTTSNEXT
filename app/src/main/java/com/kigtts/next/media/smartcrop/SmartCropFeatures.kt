/* Adapted from smartcrop.js (MIT), Jonas Wagner. */
package com.kigtts.next.media.smartcrop

import kotlin.math.*

internal object SmartCropFeatures {
    // Preserve the upstream CIE coefficients (including its blue weight), rather than substitute luma.
    private fun light(color: Int) = .5126 * (color and 255) + .7152 * ((color ushr 8) and 255) + .0722 * ((color ushr 16) and 255)
    fun detect(image: CropImage, o: SmartCropOptions, check: () -> Unit): CropImage {
        val w = image.width; val h = image.height; val input = image.pixels; val output = IntArray(input.size)
        for (y in 0 until h) {
            check()
            for (x in 0 until w) {
                val index = y * w + x; val color = input[index]; val brightness = light(color)
                val edge = if (x == 0 || y == 0 || x == w - 1 || y == h - 1) brightness
                    else brightness * 4 - light(input[index - w]) - light(input[index - 1]) - light(input[index + 1]) - light(input[index + w])
                val r = ((color ushr 16) and 255).toDouble(); val g = ((color ushr 8) and 255).toDouble(); val b = (color and 255).toDouble()
                val magnitude = sqrt(r * r + g * g + b * b)
                val skin = if (magnitude == 0.0) 0.0 else 1 - sqrt(
                    (r / magnitude - o.skinColor[0]).pow(2) + (g / magnitude - o.skinColor[1]).pow(2) + (b / magnitude - o.skinColor[2]).pow(2))
                val skinValue = if (skin > o.skinThreshold && brightness / 255 in o.skinBrightnessMin..o.skinBrightnessMax)
                    (skin - o.skinThreshold) * (255 / (1 - o.skinThreshold)) else 0.0
                val maximum = max(r, max(g, b)) / 255; val minimum = min(r, min(g, b)) / 255
                val saturation = if (maximum == minimum) 0.0 else (maximum - minimum) /
                    (if ((maximum + minimum) / 2 > .5) 2 - maximum - minimum else maximum + minimum)
                val satValue = if (saturation > o.saturationThreshold && brightness / 255 in o.saturationBrightnessMin..o.saturationBrightnessMax)
                    (saturation - o.saturationThreshold) * (255 / (1 - o.saturationThreshold)) else 0.0
                output[index] = (CropResample.byte(skinValue) shl 16) or (CropResample.byte(edge) shl 8) or CropResample.byte(satValue)
            }
        }
        for (boost in o.boosts) {
            val endX = (boost.x.toLong() + boost.width).coerceIn(0, w.toLong()).toInt()
            val endY = (boost.y.toLong() + boost.height).coerceIn(0, h.toLong()).toInt()
            for (y in boost.y.coerceIn(0, h) until endY) for (x in boost.x.coerceIn(0, w) until endX) {
                val i = y * w + x
                output[i] = (output[i] and 0xffffff) or (CropResample.byte((output[i] ushr 24) + boost.weight * 255) shl 24)
            }
        }
        return CropImage(w, h, output)
    }

    fun downSample(image: CropImage, factor: Int): CropImage {
        val w = image.width / factor; val h = image.height / factor; val output = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val sums = DoubleArray(4); val maximums = IntArray(2)
            for (dy in 0 until factor) for (dx in 0 until factor) {
                val c = image.pixels[(y * factor + dy) * image.width + x * factor + dx]
                val channels = intArrayOf((c ushr 16) and 255, (c ushr 8) and 255, c and 255, c ushr 24)
                for (i in 0..3) sums[i] += channels[i]
                for (i in 0..1) maximums[i] = max(maximums[i], channels[i])
            }
            val count = factor * factor
            output[y * w + x] = (CropResample.byte(sums[3] / count) shl 24) or
                (CropResample.byte(sums[0] / count * .5 + maximums[0] * .5) shl 16) or
                (CropResample.byte(sums[1] / count * .7 + maximums[1] * .3) shl 8) or CropResample.byte(sums[2] / count)
        }
        return CropImage(w, h, output)
    }
}
