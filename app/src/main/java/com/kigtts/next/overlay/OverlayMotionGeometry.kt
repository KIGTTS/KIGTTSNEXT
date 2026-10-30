package com.kigtts.next.overlay

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

internal fun OverlayPlacement.centerOnScreen() = Offset(x + fabX, y + fabY)
internal fun OverlayPlacement.local(point: Offset) = point - Offset(x.toFloat(), y.toFloat())

/** The touch rectangle follows the visible part, even while pulling a half-hidden FAB out. */
internal fun overlayDragPlacement(safe: OverlayBounds, density: Float, point: Offset): OverlayPlacement {
    val half = min(28f * density, min(safe.width, safe.height) / 2f)
    val range = overlayAnchorRange(safe, density)
    val center = Offset(point.x.coerceIn(safe.left.toFloat(), (safe.left + safe.width).toFloat()),
        point.y.coerceIn(range.start, range.endInclusive))
    val left = max(safe.left, floor(center.x - half).toInt())
    val top = max(safe.top, floor(center.y - half).toInt())
    val right = min(safe.left + safe.width, ceil(center.x + half).toInt())
    val bottom = min(safe.top + safe.height, ceil(center.y + half).toInt())
    return OverlayPlacement(left, top, (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1), center.x - left, center.y - top)
}
