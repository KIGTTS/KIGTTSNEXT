package com.kigtts.next.ui.home

import kotlin.math.abs

/** Whole-column stops keep phrase widths usable and reserve space for the main caption. */
class LandscapePanelGeometry(availableWidth: Float) {
    val maximumColumns = (1..6).lastOrNull { widthFor(it) <= availableWidth } ?: 1
    val anchors = listOf(COLLAPSED) + (1..maximumColumns).map(::widthFor)
    val maximum: Float get() = anchors.last()
    fun widthFor(columns: Int): Float = CHROME + 16f + columns * TILE + (columns - 1) * GAP
    fun columnsAt(width: Float): Int = (1..maximumColumns).minBy { abs(widthFor(it) - width) }
    fun damp(raw: Float): Float {
        val bounded = raw.coerceIn(COLLAPSED, maximum)
        val anchor = anchors.minBy { abs(it - bounded) }
        val distance = bounded - anchor
        val fraction = (abs(distance) / 24f).coerceIn(0f, 1f)
        val smooth = fraction * fraction * (3f - 2f * fraction)
        return anchor + distance * (.55f + .45f * smooth)
    }
    fun settle(width: Float, velocity: Float): Float = when {
        width <= COLLAPSED + 12f -> COLLAPSED
        velocity < -600f -> anchors.firstOrNull { it > width + 12f } ?: maximum
        velocity > 600f -> anchors.lastOrNull { it < width - 12f } ?: COLLAPSED
        else -> anchors.minBy { abs(it - width) }
    }
    companion object {
        const val COLLAPSED = 64f
        const val CHROME = 40f
        const val TILE = 128f
        const val GAP = 4f
        const val MAIN_MINIMUM = 220f
        const val RAIL = 64f
    }
}
