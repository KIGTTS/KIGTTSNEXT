package com.kigtts.next.media.color

import android.graphics.Bitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.floor

/** The reduced field is built from the unmodified photo before its output bands are written. */
internal class PhotoBloomField private constructor(val width: Int, val height: Int, private val rgb: FloatArray) {
    fun sample(x: Float, y: Float, channel: Int): Float {
        val xx = x.coerceIn(0f, width - 1f)
        val yy = y.coerceIn(0f, height - 1f)
        val x0 = xx.toInt(); val y0 = yy.toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1); val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = xx - x0; val fy = yy - y0
        val a = rgb[(y0 * width + x0) * 3 + channel]
        val b = rgb[(y0 * width + x1) * 3 + channel]
        val c = rgb[(y1 * width + x0) * 3 + channel]
        val d = rgb[(y1 * width + x1) * 3 + channel]
        val top = a + (b - a) * fx
        return top + (c + (d - c) * fx - top) * fy
    }

    companion object {
        suspend fun create(bitmap: Bitmap): PhotoBloomField {
            val (width, height) = PhotoBloom.size(bitmap.width, bitmap.height)
            val bright = FloatArray(width * height * 3)
            val row = IntArray(bitmap.width)
            for (y in 0 until height) {
                currentCoroutineContext().ensureActive()
                for (dy in listOf(.25f, .75f)) {
                    val sy = floor((y + dy) * bitmap.height / height).toInt().coerceAtMost(bitmap.height - 1)
                    bitmap.getPixels(row, 0, bitmap.width, 0, sy, bitmap.width, 1)
                    for (x in 0 until width) {
                        accumulate(bright, (y * width + x) * 3, pixelAt(row, x + .25f, width))
                        accumulate(bright, (y * width + x) * 3, pixelAt(row, x + .75f, width))
                    }
                }
            }
            val near = blur(PhotoBloomField(width, height, bright), width, height)
            val far = blur(near, (width / 2).coerceAtLeast(1), (height / 2).coerceAtLeast(1))
            for (y in 0 until height) for (x in 0 until width) for (channel in 0..2) {
                val offset = (y * width + x) * 3 + channel
                val broad = far.sample((x + .5f) * far.width / width - .5f, (y + .5f) * far.height / height - .5f, channel)
                near.rgb[offset] = (near.rgb[offset] * PhotoBloom.NEAR + broad * PhotoBloom.FAR).coerceIn(0f, 1f)
            }
            return near
        }

        private fun pixelAt(row: IntArray, position: Float, width: Int): Int =
            row[(position * row.size / width).toInt().coerceAtMost(row.lastIndex)]

        private fun accumulate(target: FloatArray, offset: Int, color: Int) {
            val r = (color ushr 16 and 255) / 255f
            val g = (color ushr 8 and 255) / 255f
            val b = (color and 255) / 255f
            val amount = PhotoBloom.highlight(r, g, b) * .25f
            target[offset] += r * amount
            target[offset + 1] += g * amount
            target[offset + 2] += b * amount
        }

        private suspend fun blur(source: PhotoBloomField, width: Int, height: Int): PhotoBloomField =
            blurAxis(blurAxis(source, width, height, horizontal = true), width, height, horizontal = false)

        private suspend fun blurAxis(source: PhotoBloomField, width: Int, height: Int, horizontal: Boolean): PhotoBloomField {
            val values = FloatArray(width * height * 3)
            val weights = PhotoBloom.gaussian
            for (y in 0 until height) {
                currentCoroutineContext().ensureActive()
                for (x in 0 until width) for (channel in 0..2) {
                    fun read(offset: Int): Float {
                        val xx = (x + .5f + if (horizontal) offset else 0) * source.width / width - .5f
                        val yy = (y + .5f + if (horizontal) 0 else offset) * source.height / height - .5f
                        return source.sample(xx, yy, channel)
                    }
                    var sum = read(0) * weights[0]
                    for (radius in 1..weights.lastIndex) sum += (read(radius) + read(-radius)) * weights[radius]
                    values[(y * width + x) * 3 + channel] = sum
                }
            }
            return PhotoBloomField(width, height, values)
        }
    }
}
