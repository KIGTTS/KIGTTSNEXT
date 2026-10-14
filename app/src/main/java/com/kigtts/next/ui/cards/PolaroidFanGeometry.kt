package com.kigtts.next.ui.cards

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun polaroidBrowseWidth(viewportWidth: Dp, viewportHeight: Dp, aspect: Float, wide: Boolean): Dp =
    minOf(viewportWidth * (if (wide) .44f else .78f),
        viewportHeight * (if (wide) .78f else .86f) * aspect, 460.dp).coerceAtLeast(1.dp)

/** Open a gap before the foreground changes, then close it as the new photo settles. */
internal fun polaroidFanSpacing(viewportWidth: Float, paperWidth: Float, pageOffset: Float): Float {
    val rest = minOf(paperWidth * .72f, viewportWidth * .28f)
    val progress = (kotlin.math.abs(pageOffset) * 4f).coerceIn(0f, 1f)
    val reveal = progress * progress * (3f - 2f * progress)
    return rest + (paperWidth * 1.16f - rest) * reveal
}
