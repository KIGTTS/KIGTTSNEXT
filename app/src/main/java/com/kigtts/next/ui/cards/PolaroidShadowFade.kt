package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/** Group opacity needs a larger texture than the paper; otherwise its elevation is clipped. */
internal fun Modifier.polaroidShadowFade(opacity: () -> Float): Modifier = layout { child, constraints ->
    val inset = 12.dp.roundToPx()
    val placed = child.measure(constraints.offset(horizontal = inset * 2, vertical = inset * 2))
    layout(placed.width - inset * 2, placed.height - inset * 2) { placed.place(-inset, -inset) }
}.graphicsLayer { alpha = opacity() }.padding(12.dp)
