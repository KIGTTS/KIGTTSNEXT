package com.kigtts.next.ui.cards

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Retain one full panel for drawing while the parent allocates only the new endpoint's space. */
internal fun Modifier.cardDockGeometry(fullWidth: Dp, fullHeight: Dp, landscape: Boolean,
    phase: State<Float>, shape: Shape): Modifier = layout { measurable, constraints ->
    val width = fullWidth.roundToPx(); val height = fullHeight.roundToPx()
    val child = measurable.measure(Constraints.fixed(width, height))
    val slotWidth = constraints.maxWidth; val slotHeight = constraints.maxHeight
    layout(slotWidth, slotHeight) { child.placeRelative(slotWidth - width, slotHeight - height) }
}.drawWithContent {
    val fraction = phase.value.coerceIn(0f, 1f)
    val collapsedWidth = minOf(54.dp.toPx(), size.width)
    val collapsedHeight = minOf(60.dp.toPx(), size.height)
    val visibleWidth = if (landscape) collapsedWidth + (size.width - collapsedWidth) * fraction else size.width
    val visibleHeight = if (landscape) size.height else collapsedHeight + (size.height - collapsedHeight) * fraction
    val shift = Offset(size.width - visibleWidth, size.height - visibleHeight)
    val outline = shape.createOutline(Size(visibleWidth, visibleHeight), layoutDirection, this)
    val path = Path().apply {
        when (outline) {
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Generic -> addPath(outline.path)
        }
        translate(shift)
    }
    clipPath(path) { this@drawWithContent.drawContent() }
}
