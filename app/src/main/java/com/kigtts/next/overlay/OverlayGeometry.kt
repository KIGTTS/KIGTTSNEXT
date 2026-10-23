package com.kigtts.next.overlay

import kotlin.math.*

/** Pure geometry shared by the real WindowManager host and rotation/cutout regression tests. */
internal data class OverlayBounds(val left: Int, val top: Int, val width: Int, val height: Int)
internal data class OverlayPlacement(val x: Int, val y: Int, val width: Int, val height: Int, val fabX: Float, val fabY: Float)

internal fun overlayPlacement(safe: OverlayBounds, density: Float, right: Boolean, position: Float,
    expanded: Boolean): OverlayPlacement {
    val unit = density.coerceAtLeast(.1f)
    val width = min(safe.width, ((if (expanded) 296 else 80) * unit).roundToInt())
    val height = min(safe.height, ((if (expanded) 356 else 80) * unit).roundToInt())
    val fab = min(40 * unit, min(width, height) / 2f)
    val anchorY = safe.top + fab + (safe.height - 2 * fab).coerceAtLeast(0f) * position.coerceIn(0f, 1f)
    val y = (anchorY - height / 2f).roundToInt().coerceIn(safe.top, safe.top + (safe.height - height).coerceAtLeast(0))
    return OverlayPlacement(if (right) safe.left + safe.width - width else safe.left, y, width, height,
        if (right) width - fab else fab, (anchorY - y).coerceIn(fab, height - fab))
}

internal fun dialPoint(slot: Int, right: Boolean, radius: Float): Pair<Float, Float> {
    val angle = Math.toRadians((slot - 2) * 36.0)
    return (if (right) -1 else 1) * cos(angle).toFloat() * radius to sin(angle).toFloat() * radius
}
