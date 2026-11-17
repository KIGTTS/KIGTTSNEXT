package com.kigtts.next.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlin.math.floor

const val SUBTITLE_READABLE_MINIMUM = 24f

internal val LocalSubtitleFitCache = staticCompositionLocalOf<SubtitleFitCache?> { null }

@Composable
internal fun rememberSubtitleFitCache(): SubtitleFitCache {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    return remember(measurer) { SubtitleFitCache(measurer) }
}

/** Keep measurement results through the switch between the display and editable paragraph. */
internal class SubtitleFitCache(private val measurer: TextMeasurer) {
    private data class FitKey(val text: AnnotatedString, val style: TextStyle, val width: Int)
    private val fits = object : LinkedHashMap<FitKey, MutableMap<Float, Pair<Int, Boolean>>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FitKey, MutableMap<Float, Pair<Int, Boolean>>>?): Boolean = size > 12
    }

    fun measure(text: AnnotatedString, style: TextStyle, width: Int, height: Int): Float {
        val minimum = minOf(SUBTITLE_READABLE_MINIMUM, style.fontSize.value)
        if (width <= 0 || height <= 0) return minimum
        val key = FitKey(text, style.copy(color = Color.Unspecified), width)
        val metrics = fits.getOrPut(key) { mutableMapOf() }
        fun fits(size: Float): Boolean {
            val result = metrics.getOrPut(size) {
                val layout = measurer.measure(text, key.style.copy(fontSize = size.sp), constraints = Constraints(maxWidth = width))
                layout.size.height to layout.didOverflowWidth
            }
            return !result.second && result.first <= height
        }
        val maximum = style.fontSize.value
        if (fits(maximum)) return maximum
        var low = minimum.toInt()
        var high = floor(maximum).toInt()
        var best = minimum
        while (low <= high) {
            val candidate = (low + high) / 2
            if (fits(candidate.toFloat())) { best = candidate.toFloat(); low = candidate + 1 }
            else high = candidate - 1
        }
        return best
    }
}

/** Paragraph height depends on width, not on viewport height; reuse it during sheet motion. */
@Composable
internal fun fittingSubtitleFontSize(text: String, style: TextStyle, width: Int, height: Int): Float =
    fittingSubtitleFontSize(AnnotatedString(text), style, width, height)

@Composable
internal fun fittingSubtitleFontSize(text: AnnotatedString, style: TextStyle, width: Int, height: Int): Float {
    val density = LocalDensity.current.density
    val cache = LocalSubtitleFitCache.current ?: rememberSubtitleFitCache()
    // A conservative width bucket limits line-break recalculation during horizontal transitions.
    val bucket = (8f * density).toInt().coerceAtLeast(1)
    val measuredWidth = ((width / bucket) * bucket).coerceAtLeast(1)
    return cache.measure(text, style, if (width > 0) measuredWidth else 0, height)
}
