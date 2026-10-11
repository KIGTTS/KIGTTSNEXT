package com.kigtts.next.ui.home

import com.kigtts.next.state.HomePanelSizes
import kotlin.math.abs

/** Keep the single-row peek; expanded stops start at two complete phrase rows. */
class PortraitPanelGeometry(availableHeight: Float) {
    private val limit = (availableHeight - 64f - 180f).coerceAtLeast(HomePanelSizes.PEEK)
    fun heightFor(rows: Int) = HomePanelSizes.EXPANDED_CHROME + rows * HomePanelSizes.ROW_HEIGHT + (rows - 1) * HomePanelSizes.ROW_GAP
    val anchors = buildList {
        add(HomePanelSizes.COLLAPSED); add(HomePanelSizes.PEEK)
        // Fully visible rows include both 48dp chrome bars and the gaps between tiles.
        if (limit < heightFor(2)) add(limit)
        else { var rows = 2; while (heightFor(rows) <= limit) add(heightFor(rows++)) }
    }.distinct().sorted()
    val maximum get() = anchors.last()
    fun nearest(height: Float) = anchors.minBy { abs(it - height) }
    fun settle(height: Float, velocity: Float): Float = when {
        height <= HomePanelSizes.COLLAPSED + 12f -> HomePanelSizes.COLLAPSED
        velocity < -600f -> anchors.firstOrNull { it > height + 12f } ?: maximum
        velocity > 600f -> anchors.lastOrNull { it < height - 12f } ?: HomePanelSizes.COLLAPSED
        else -> nearest(height)
    }
}
