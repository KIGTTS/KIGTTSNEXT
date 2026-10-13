package com.kigtts.next.ui.cards

import com.kigtts.plugin.api.*

internal val editablePolaroidFormats = listOf(PolaroidFormat.MINI, PolaroidFormat.WIDE, PolaroidFormat.SQUARE)

internal fun polaroidFormat(card: PolaroidCard, frame: PolaroidFrameStyle?): PolaroidFormat =
    frame?.format ?: when (card.frameStyleId.split('.').getOrNull(1)?.takeIf { card.frameStyleId.startsWith("instax.") }) {
        "mini" -> PolaroidFormat.MINI
        "wide" -> PolaroidFormat.WIDE
        "square" -> PolaroidFormat.SQUARE
        else -> PolaroidFormat.LEGACY
    }

internal fun selectPolaroidFormat(card: PolaroidCard, format: PolaroidFormat,
    frames: List<PolaroidFrameStyle>): PolaroidCard {
    val variants = frames.filter { it.format == format }
    val current = frames.firstOrNull { it.id == card.frameStyleId }
    val target = variants.firstOrNull { it.id.substringAfterLast('.') == current?.id?.substringAfterLast('.') }
        ?: variants.firstOrNull { it.id.substringAfterLast('.') == "classic" }
        ?: variants.firstOrNull() ?: return card
    return card.copy(frameStyleId = target.id)
}
