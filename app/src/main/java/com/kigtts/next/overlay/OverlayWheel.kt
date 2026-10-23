package com.kigtts.next.overlay

import kotlin.math.abs

internal data class OverlayWheelPosition(val first: Int = 0, val drag: Float = 0f)

/** Five visible entrances, with real endpoints instead of wrapping duplicate items. */
internal fun dragOverlayWheel(position: OverlayWheelPosition, amount: Float, count: Int, step: Float): OverlayWheelPosition {
    val last = (count - 5).coerceAtLeast(0)
    var first = position.first.coerceIn(0, last)
    var drag = position.drag + amount
    while (drag <= -step && first < last) { first++; drag += step }
    while (drag >= step && first > 0) { first--; drag -= step }
    return OverlayWheelPosition(first, drag)
}

internal fun overlayWheelStretch(position: OverlayWheelPosition, count: Int, step: Float): Float {
    val last = (count - 5).coerceAtLeast(0)
    val atEdge = position.first == 0 && position.drag > 0 || position.first == last && position.drag < 0
    return if (atEdge) position.drag / (1f + abs(position.drag) / (step * .65f)) else position.drag
}

internal fun settleOverlayWheel(position: OverlayWheelPosition, count: Int, step: Float): OverlayWheelPosition {
    val move = when { position.drag < -step / 2 -> 1; position.drag > step / 2 -> -1; else -> 0 }
    return OverlayWheelPosition((position.first + move).coerceIn(0, (count - 5).coerceAtLeast(0)))
}
