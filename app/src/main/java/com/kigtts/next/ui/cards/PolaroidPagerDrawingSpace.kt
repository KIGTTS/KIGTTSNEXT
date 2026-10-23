package com.kigtts.next.ui.cards

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * A rising dock allocates its endpoint immediately, but its drawing mask moves gradually.
 * Keep drawing room below the viewport while old, larger papers settle into the smaller dock.
 * The extra room and matching bottom content padding leave the page's origin unchanged.
 * Negative pager placement is not safe here: LazyLayout's lookahead coordinates omit that
 * offset when a shared paper first moves into the overlay.
 */
internal fun Modifier.polaroidPagerDrawingSpace(bottomPadding: Dp): Modifier = layout { child, constraints ->
    val extra = bottomPadding.roundToPx()
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placed = child.measure(Constraints.fixed(width, height + extra))
    layout(width, height) { placed.place(0, 0) }
}
