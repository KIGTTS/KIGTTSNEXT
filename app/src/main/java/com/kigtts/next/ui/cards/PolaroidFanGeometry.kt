package com.kigtts.next.ui.cards

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun polaroidBrowseWidth(viewportWidth: Dp, viewportHeight: Dp, aspect: Float, wide: Boolean): Dp =
    minOf(viewportWidth * (if (wide) .44f else .78f),
        viewportHeight * (if (wide) .78f else .86f) * aspect, 460.dp).coerceAtLeast(1.dp)

/** Adjacent papers occupy each side of the focused photo; no role changes at a half-page boundary. */
internal fun polaroidFanSpacing(viewportWidth: Float, paperWidth: Float): Float =
    minOf(paperWidth * .72f, viewportWidth * .28f)
