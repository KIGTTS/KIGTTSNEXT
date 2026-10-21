package com.kigtts.next.media.color

import android.graphics.Bitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

/** Bounded source bands retain untouched halo rows; no second full-resolution bitmap is needed. */
internal suspend fun applyPhotoEffects(image: Bitmap, effects: PhotoEffects) {
    if (!effects.active) return
    require(image.isMutable)
    val width = image.width; val height = image.height
    val radius = if (effects.aberration > 0f || effects.bloom > 0f)
        (ceil(height * maxOf(.01f * effects.aberration, .008f * effects.bloom)).toInt() + 1).coerceAtMost(height) else 0
    val band = minOf(32, height)
    val source = IntArray(width * (band + 2 * radius))
    val carry = IntArray(width * radius)
    val output = IntArray(width * band)
    var y = 0
    while (y < height) {
        currentCoroutineContext().ensureActive()
        val rows = minOf(band, height - y); val top = maxOf(0, y - radius)
        val bottom = minOf(height, y + rows + radius); val prefix = y - top
        if (prefix > 0) carry.copyInto(source, 0, 0, prefix * width)
        image.getPixels(source, prefix * width, width, 0, y, width, bottom - y)
        fun sample(x: Float, row: Float, shift: Int): Float {
            val xx = x.coerceIn(0f, width - 1f); val yy = row.coerceIn(0f, height - 1f)
            val x0 = xx.toInt(); val y0 = yy.toInt(); val x1 = minOf(x0 + 1, width - 1); val y1 = minOf(y0 + 1, height - 1)
            fun at(a: Int, b: Int) = (source[(b - top) * width + a] ushr shift and 255) / 255f
            val fx = xx - x0; val fy = yy - y0
            val low = at(x0, y0) * (1 - fx) + at(x1, y0) * fx
            val high = at(x0, y1) * (1 - fx) + at(x1, y1) * fx
            return low * (1 - fy) + high * fy
        }
        for (row in 0 until rows) for (x in 0 until width) {
            val yy = y + row; val uvX = (x + .5f) / width; val uvY = (yy + .5f) / height
            val dx = uvX - .5f; val dy = uvY - .5f; val edge = (dx * dx + dy * dy) * 2f
            val smooth = ((edge - .15f) / .7f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
            val n = filmGrain(floor(uvX * 2048).toInt(), floor(uvY * 2048).toInt())
            val original = source[(yy - top) * width + x]
            var color = original and -0x1000000
            for (channel in 0..2) {
                val shift = 16 - channel * 8
                var value = (original ushr shift and 255) / 255f
                if (effects.aberration > 0f && channel != 1) {
                    val sign = if (channel == 0) 1f else -1f
                    value = sample(x + dx * .018f * edge * effects.aberration * width * sign,
                        yy + dy * .018f * edge * effects.aberration * height * sign, shift)
                }
                if (effects.bloom > 0f) {
                    val rx = width * .008f; val ry = height * .008f
                    val light = maxOf(0f, sample(x + rx, yy.toFloat(), shift) - .65f) + maxOf(0f, sample(x - rx, yy.toFloat(), shift) - .65f) +
                        maxOf(0f, sample(x.toFloat(), yy + ry, shift) - .65f) + maxOf(0f, sample(x.toFloat(), yy - ry, shift) - .65f)
                    value = 1f - (1f - value) * (1f - light / 1.4f * .4f * effects.bloom)
                }
                value = value * (1 - .72f * effects.vignette * smooth) + n * .12f * effects.grain
                color = color or ((value.coerceIn(0f, 1f) * 255).roundToInt() shl shift)
            }
            output[row * width + x] = color
        }
        val retained = minOf(radius, y + rows)
        if (retained > 0) source.copyInto(carry, 0, (y + rows - retained - top) * width, (y + rows - top) * width)
        image.setPixels(output, 0, width, 0, y, width, rows)
        y += rows
    }
}
