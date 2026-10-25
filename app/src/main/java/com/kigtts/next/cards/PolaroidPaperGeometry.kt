package com.kigtts.next.cards

import com.kigtts.plugin.api.*
import com.kigtts.next.ui.cards.polaroidFormat

internal fun polaroidPaperGeometry(card: PolaroidCard, frame: PolaroidFrameStyle? = null): PolaroidGeometry {
    val regular = polaroidFormat(card, frame).geometry
    if (card.frameStyleId != "identity.banner" && !card.frameStyleId.startsWith("received.")) return regular
    val banner = card.frameStyleId in setOf("identity.banner", "received.banner")
    val width = if (banner) 160.0 else regular.paperWidth
    val height = if (banner) 90.0 else regular.paperHeight
    return PolaroidGeometry(width, height, 0.0, 0.0, width, height, 0.0, height + 10, width, 3.2, height + 20, 2.0, true)
}

internal fun isFullPhotoPaper(card: PolaroidCard) = card.frameStyleId == "identity.banner" || card.frameStyleId.startsWith("received.")
