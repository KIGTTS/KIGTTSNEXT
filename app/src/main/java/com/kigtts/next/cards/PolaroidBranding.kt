package com.kigtts.next.cards

import com.kigtts.plugin.api.PolaroidCard
import com.kigtts.plugin.api.PolaroidFrameStyle
import com.kigtts.plugin.api.PolaroidFormat

/** Host branding is paper decoration, never a compulsory overlay on third-party frames. */
internal data class PolaroidBranding(val x: Double, val y: Double, val width: Double,
    val height: Double, val captionWidth: Double)

internal fun polaroidBranding(card: PolaroidCard, frame: PolaroidFrameStyle? = null): PolaroidBranding? {
    if (card.frameStyleId !in setOf("classic", "instax.mini.classic", "instax.square.classic", "instax.wide.classic") ||
        frame?.owner != null || frame?.overlayImageId != null || (frame != null && frame.id != card.frameStyleId)) return null
    val geometry = (frame?.format ?: when (card.frameStyleId) {
        "instax.mini.classic" -> PolaroidFormat.MINI
        "instax.square.classic" -> PolaroidFormat.SQUARE
        "instax.wide.classic" -> PolaroidFormat.WIDE
        else -> PolaroidFormat.LEGACY
    }).geometry
    val width = geometry.paperWidth * .2
    val height = width / 4.0 // Existing horizontal logo viewport is 4096 × 1024.
    val margin = if (geometry.millimeterSizing) 2.5 else 48.0
    val gap = if (geometry.millimeterSizing) 1.6 else 28.0
    val x = geometry.photoRight - width
    return PolaroidBranding(x, geometry.paperHeight - margin - height, width, height,
        minOf(geometry.titleWidth, x - geometry.titleX - gap))
}
