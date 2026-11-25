package com.kigtts.next.ui.onboarding

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal fun homeTipOffset(
    viewport: IntSize,
    card: IntSize,
    anchor: Rect,
    margin: Int,
    preferSide: Boolean,
): IntOffset? {
    if (anchor.isEmpty || !anchor.overlaps(Rect(0f, 0f, viewport.width.toFloat(), viewport.height.toFloat()))) return null
    val maxX = viewport.width - margin - card.width
    val maxY = viewport.height - margin - card.height
    if (maxX < margin || maxY < margin || card.width == 0 || card.height == 0) return null

    val centerX = (anchor.center.x - card.width / 2f).roundToInt().coerceIn(margin, maxX)
    val centerY = (anchor.center.y - card.height / 2f).roundToInt().coerceIn(margin, maxY)
    val above = floor(anchor.top).toInt() - margin - card.height
    val below = ceil(anchor.bottom).toInt() + margin
    val left = floor(anchor.left).toInt() - margin - card.width
    val right = ceil(anchor.right).toInt() + margin

    fun vertical() = when {
        above in margin..maxY -> IntOffset(centerX, above)
        below in margin..maxY -> IntOffset(centerX, below)
        else -> null
    }
    fun horizontal() = when {
        left in margin..maxX -> IntOffset(left, centerY)
        right in margin..maxX -> IntOffset(right, centerY)
        else -> null
    }
    return if (preferSide) horizontal() ?: vertical() else vertical() ?: horizontal()
}
