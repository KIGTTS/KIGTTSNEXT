package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import kotlin.math.roundToInt

/** Retain the strip through exit, with stable toolbar height and immediate input cancellation. */
@Composable
internal fun CursorTrackSlot(editing: Boolean, model: AppViewModel, reveal: State<Float>, modifier: Modifier) {
    val visible by remember(editing, reveal) { derivedStateOf { editing || reveal.value > 0f } }
    Box(modifier.height(32.dp).clipToBounds().testTag("cursor-slot"), contentAlignment = Alignment.Center) {
        if (visible) {
            Box(Modifier.layout { measurable, constraints ->
                val width = (constraints.maxWidth * (.6f + .4f * reveal.value.coerceIn(0f, 1f))).roundToInt()
                val child = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(width, child.height) { child.placeRelative(0, 0) }
            }.graphicsLayer { alpha = reveal.value.coerceIn(0f, 1f) }) {
                CursorTrack(model, Modifier.fillMaxWidth(), enabled = editing)
            }
        }
        if (!editing) Spacer(Modifier.fillMaxSize().testTag("editor-bottom-reserved"))
    }
}

/** Preserve the three animated widths without recomposing the button groups every frame. */
@Composable
internal fun EditorFooterSlots(reveal: State<Float>, modifier: Modifier,
    start: @Composable () -> Unit, middle: @Composable () -> Unit, end: @Composable () -> Unit) {
    Layout(modifier = modifier, content = {
        Box(contentAlignment = Alignment.CenterStart) { start() }
        Box(contentAlignment = Alignment.Center) { middle() }
        Box(contentAlignment = Alignment.CenterEnd) { end() }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val middleWidth = (width * (.2f + .3f * reveal.value.coerceIn(0f, 1f))).roundToInt()
        val startWidth = (width - middleWidth + 1) / 2
        val widths = intArrayOf(startWidth, middleWidth, width - startWidth - middleWidth)
        val children = measurables.mapIndexed { index, item ->
            item.measure(constraints.copy(minWidth = widths[index], maxWidth = widths[index], minHeight = 0))
        }
        val height = children.maxOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var x = 0
            children.forEach { child ->
                child.placeRelative(x, (height - child.height) / 2)
                x += child.width
            }
        }
    }
}
