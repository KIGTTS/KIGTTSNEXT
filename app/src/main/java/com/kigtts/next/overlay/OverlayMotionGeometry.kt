package com.kigtts.next.overlay

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

internal fun OverlayPlacement.centerOnScreen() = Offset(x + fabX, y + fabY)
internal fun OverlayPlacement.local(point: Offset) = point - Offset(x.toFloat(), y.toFloat())

private fun overlayDragDiameter(safe: OverlayBounds, density: Float): Int =
    (56f * density).roundToInt().coerceIn(1, min(safe.width, safe.height))

/** Discard overshoot at the boundary so reversing the gesture immediately moves the ball. */
internal fun clampOverlayDragCenter(safe: OverlayBounds, density: Float, point: Offset): Offset {
    val half = overlayDragDiameter(safe, density) / 2f
    val range = overlayAnchorRange(safe, density)
    return Offset(point.x.coerceIn(safe.left + half, safe.left + safe.width - half),
        point.y.coerceIn(range.start, range.endInclusive))
}

/** Dragging and docking can move the same surface without changing its buffer size. */
internal fun overlayDragPlacement(safe: OverlayBounds, density: Float, point: Offset): OverlayPlacement {
    val diameter = overlayDragDiameter(safe, density)
    val half = diameter / 2f
    val center = clampOverlayDragCenter(safe, density, point)
    val left = (center.x - half).roundToInt().coerceIn(safe.left, safe.left + safe.width - diameter)
    val top = (center.y - half).roundToInt().coerceIn(safe.top, safe.top + safe.height - diameter)
    return OverlayPlacement(left, top, diameter, diameter, half, half)
}
