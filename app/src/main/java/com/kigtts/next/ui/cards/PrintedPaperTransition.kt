package com.kigtts.next.ui.cards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.kigtts.next.state.CardPrintOrigin

/** Printer coordinates are window pixels, matching the edge-to-edge native preview window. */
internal fun printedPaperStart(origin: CardPrintOrigin, window: IntSize): Rect {
    val xScale = if (origin.viewportWidth > 0f && window.width > 0) window.width / origin.viewportWidth else 1f
    val yScale = if (origin.viewportHeight > 0f && window.height > 0) window.height / origin.viewportHeight else 1f
    val scale = minOf(xScale, yScale)
    val center = Offset((origin.left + origin.width / 2f) * xScale, (origin.top + origin.height / 2f) * yScale)
    val half = Offset(origin.width * scale / 2f, origin.height * scale / 2f)
    return Rect(center - half, center + half)
}

internal data class PrintedPaperPose(val scale: Float, val translation: Offset)

/** One uniform transform preserves paper, photograph, caption and logo throughout the handoff. */
internal fun printedPaperPose(start: Rect, targetCenter: Offset, paperWidth: Float, fraction: Float): PrintedPaperPose {
    val phase = fraction.coerceIn(0f, 1f)
    val scale = start.width / paperWidth.coerceAtLeast(1f)
    return PrintedPaperPose(scale + (1f - scale) * phase, (start.center - targetCenter) * (1f - phase))
}
