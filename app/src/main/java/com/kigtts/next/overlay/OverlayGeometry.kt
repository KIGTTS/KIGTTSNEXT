package com.kigtts.next.overlay

import kotlin.math.*

/** Pure geometry shared by the real WindowManager host and rotation/cutout regression tests. */
internal data class OverlayBounds(val left: Int, val top: Int, val width: Int, val height: Int)
internal data class OverlayPlacement(val x: Int, val y: Int, val width: Int, val height: Int, val fabX: Float, val fabY: Float)

internal fun OverlayPlacement.sameLocalGeometry(other: OverlayPlacement): Boolean =
    width == other.width && height == other.height && fabX == other.fabX && fabY == other.fabY

internal fun overlayPlacement(safe: OverlayBounds, density: Float, right: Boolean, position: Float,
    expanded: Boolean, retraction: Float = 0f): OverlayPlacement {
    val unit = density.coerceAtLeast(.1f)
    if (!expanded) {
        // Clip the actual input window to the visible FAB. Translating only its
        // drawing would leave an invisible rectangle intercepting the app below.
        val half = min(28 * unit, min(safe.width, safe.height) / 2f)
        val margin = min(40 * unit, min(safe.width, safe.height) / 2f)
        val centerY = safe.top + margin + (safe.height - 2 * margin).coerceAtLeast(0f) * position.coerceIn(0f, 1f)
        val inset = margin * (1f - retraction.coerceIn(0f, 1f))
        val centerX = if (right) safe.left + safe.width - inset else safe.left + inset
        val left = max(safe.left, (centerX - half).roundToInt())
        val top = max(safe.top, (centerY - half).roundToInt())
        val end = min(safe.left + safe.width, (centerX + half).roundToInt())
        val bottom = min(safe.top + safe.height, (centerY + half).roundToInt())
        return OverlayPlacement(left, top, (end - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1), centerX - left, centerY - top)
    }
    val width = min(safe.width, (296 * unit).roundToInt())
    val height = min(safe.height, (356 * unit).roundToInt())
    val fab = min(40 * unit, min(width, height) / 2f)
    val anchorY = safe.top + fab + (safe.height - 2 * fab).coerceAtLeast(0f) * position.coerceIn(0f, 1f)
    val y = (anchorY - height / 2f).roundToInt().coerceIn(safe.top, safe.top + (safe.height - height).coerceAtLeast(0))
    val retract = fab * retraction.coerceIn(0f, 1f)
    return OverlayPlacement(if (right) safe.left + safe.width - width else safe.left, y, width, height,
        (if (right) width - fab + retract else fab - retract).coerceIn(0f, width.toFloat()), height / 2f)
}

/** Resize around the same absolute center before moving the complete dial into its safe bounds. */
internal fun OverlayPlacement.aroundCenterOf(previous: OverlayPlacement) = copy(
    x = (previous.x + previous.fabX - fabX).roundToInt(),
    y = (previous.y + previous.fabY - fabY).roundToInt())

internal fun dialPoint(slot: Int, right: Boolean, radius: Float): Pair<Float, Float> {
    val angle = Math.toRadians((slot - 2) * 36.0)
    return (if (right) -1 else 1) * cos(angle).toFloat() * radius to sin(angle).toFloat() * radius
}
