package com.kigtts.next.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlin.math.floor

const val SUBTITLE_READABLE_MINIMUM = 24f

/** Paragraph height depends on width, not on viewport height; reuse it during sheet motion. */
@Composable
internal fun fittingSubtitleFontSize(text: String, style: TextStyle, width: Int, height: Int): Float {
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer(cacheSize = 32)
    // A conservative width bucket limits line-break recalculation during horizontal transitions.
    val bucket = (8f * density).toInt().coerceAtLeast(1)
    val measuredWidth = ((width / bucket) * bucket).coerceAtLeast(1)
    val metrics = remember(text, style.copy(color = androidx.compose.ui.graphics.Color.Unspecified), measuredWidth, measurer) {
        mutableMapOf<Float, Pair<Int, Boolean>>()
    }
    val minimum = minOf(SUBTITLE_READABLE_MINIMUM, style.fontSize.value)
    fun fits(size: Float): Boolean {
        val result = metrics.getOrPut(size) {
            val layout = measurer.measure(text, style.copy(fontSize = size.sp),
                constraints = Constraints(maxWidth = measuredWidth))
            layout.size.height to layout.didOverflowWidth
        }
        return !result.second && result.first <= height
    }
    if (width <= 0 || height <= 0) return minimum
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
