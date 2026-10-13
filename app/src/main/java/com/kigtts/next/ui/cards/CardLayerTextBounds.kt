package com.kigtts.next.ui.cards

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/** Measure text against the paper for wrapping, but expose only its visible ink as the layer hit target. */
internal fun Modifier.cardLayerTextBounds(paperWidth: Dp, inkWidth: Dp): Modifier = layout { measurable, constraints ->
    val fullWidth = paperWidth.roundToPx().coerceIn(1, constraints.maxWidth.coerceAtLeast(1))
    val child = measurable.measure(Constraints(minWidth = fullWidth, maxWidth = fullWidth,
        minHeight = 0, maxHeight = constraints.maxHeight))
    val width = inkWidth.roundToPx().coerceIn(1, fullWidth)
    layout(width, child.height) { child.placeRelative((width - fullWidth) / 2, 0) }
}
