package com.kigtts.next.state

import com.kigtts.plugin.api.PolaroidCard

/** Measured printed-paper bounds in window pixels, including the complete frame and captions. */
data class CardPrintOrigin(val left: Float, val top: Float, val width: Float, val height: Float,
    val viewportWidth: Float, val viewportHeight: Float) {
    internal val usable: Boolean get() = listOf(left, top, width, height, viewportWidth, viewportHeight).all { it.isFinite() } &&
        width > 0f && height > 0f && viewportWidth in 1f..65536f && viewportHeight in 1f..65536f &&
        width <= viewportWidth * 4f && height <= viewportHeight * 4f && left < viewportWidth && top < viewportHeight &&
        left + width > 0f && top + height > 0f
}

/** A stable overlay owner holds the paper while the camera behind it returns to the album. */
data class CardPrintPreviewHandoff(val requestId: String, val card: PolaroidCard, val origin: CardPrintOrigin,
    val shown: Boolean = false)
