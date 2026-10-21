package com.kigtts.next.ui.cards.camera

import com.kigtts.next.media.color.CubeLut
import java.util.concurrent.ConcurrentHashMap

/** Original photographic grades. A shared LUT keeps selective color/tone curves identical in all outputs. */
internal object CameraLookPresets {
    val filters = setOf(PhotoFilter.BLACK_GOLD, PhotoFilter.INK, PhotoFilter.MONSOON, PhotoFilter.KYOTO, PhotoFilter.OLD_ALLEY)
    private val cache = ConcurrentHashMap<PhotoFilter, CubeLut>()
    fun lut(filter: PhotoFilter): CubeLut? = if (filter in filters) cache.getOrPut(filter) { create(filter) } else null

    private fun create(filter: PhotoFilter): CubeLut {
        val size = 33
        val samples = FloatArray(size * size * size * 3)
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            val rgb = grade(filter, r / 32f, g / 32f, b / 32f)
            val offset = ((b * size + g) * size + r) * 3
            for (channel in 0..2) samples[offset + channel] = rgb[channel].coerceIn(0f, 1f)
        }
        return CubeLut(filter.label, size, floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f), samples)
    }

    private fun grade(filter: PhotoFilter, r: Float, g: Float, b: Float): FloatArray {
        val y = .2126f * r + .7152f * g + .0722f * b
        val mid = 4f * y * (1f - y)
        fun tone(contrast: Float, lift: Float) = ((y - .5f) * contrast + .5f + lift).coerceIn(0f, 1f)
        fun channel(source: Float, base: Float, saturation: Float, tint: Float = 0f) = base + (source - y) * saturation + tint * mid
        return when (filter) {
            PhotoFilter.BLACK_GOLD -> {
                val cool = ((b - (r + g) / 2f) * 3f).coerceIn(0f, 1f)
                val warm = (((r + g) / 2f - b) * 3f + .2f).coerceIn(0f, 1f)
                val base = tone(1.24f, -.04f) * (1f - .38f * cool)
                floatArrayOf(channel(r, base, .18f, .18f * warm), channel(g, base, .18f, .065f * warm), channel(b, base, .12f, -.17f * warm))
            }
            PhotoFilter.INK -> {
                val base = tone(1.16f, .035f)
                floatArrayOf(channel(r, base, .12f, -.018f), channel(g, base, .12f), channel(b, base, .12f, .012f))
            }
            PhotoFilter.MONSOON -> {
                val base = tone(.96f, -.015f)
                floatArrayOf(channel(r, base, .72f, -.055f), channel(g, base, .8f, .025f), channel(b, base, .78f, .045f))
            }
            PhotoFilter.KYOTO -> {
                val base = tone(.94f, .035f)
                val foliage = ((g - maxOf(r, b)) * 2f).coerceIn(0f, 1f)
                floatArrayOf(channel(r, base, .83f, .04f), channel(g, base, .76f, -.025f * foliage), channel(b, base, .72f, -.035f))
            }
            PhotoFilter.OLD_ALLEY -> {
                val base = tone(.88f, -.015f)
                floatArrayOf(channel(r, base, .66f, .055f), channel(g, base, .6f, .008f), channel(b, base, .5f, -.065f))
            }
            else -> floatArrayOf(r, g, b)
        }
    }
}
