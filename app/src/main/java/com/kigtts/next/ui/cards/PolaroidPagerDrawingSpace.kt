package com.kigtts.next.ui.cards

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * A rising dock allocates its endpoint immediately, but its drawing mask moves gradually.
 * Retain fixed vertical drawing room behind that mask without changing a page's measured
 * content size, horizontal paging distance, or measuring again on animation frames.
 * The matching Pager contentPadding cancels this room for its page constraints.
 */
internal fun Modifier.polaroidPagerDrawingSpace(verticalPadding: Dp): Modifier = layout { child, constraints ->
    val extra = verticalPadding.roundToPx()
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placed = child.measure(Constraints.fixed(width, height + extra * 2))
    layout(width, height) { placed.place(0, -extra) }
}
