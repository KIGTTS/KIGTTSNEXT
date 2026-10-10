package com.kigtts.next.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight

/** Fast-changing sizes are observed during measure, rather than restarting composition. */
fun Modifier.heightFrom(height: () -> Float) = layout { measurable, constraints ->
    val pixels = height().coerceAtLeast(0f).dp.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
    val child = measurable.measure(constraints.copy(minHeight = pixels, maxHeight = pixels))
    layout(child.width, child.height) { child.placeRelative(0, 0) }
}

fun Modifier.widthFrom(width: () -> Float) = layout { measurable, constraints ->
    val pixels = width().coerceAtLeast(0f).dp.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
    val child = measurable.measure(constraints.copy(minWidth = pixels, maxWidth = pixels))
    layout(child.width, child.height) { child.placeRelative(0, 0) }
}

fun Modifier.horizontalPaddingFrom(padding: () -> Float) = layout { measurable, constraints ->
    val pixels = padding().coerceAtLeast(0f).dp.roundToPx()
    val child = measurable.measure(constraints.offset(horizontal = -pixels * 2))
    layout(constraints.constrainWidth(child.width + pixels * 2), child.height) { child.placeRelative(pixels, 0) }
}

fun Modifier.verticalPaddingFrom(padding: () -> Float) = layout { measurable, constraints ->
    val pixels = padding().coerceAtLeast(0f).dp.roundToPx()
    val child = measurable.measure(constraints.offset(vertical = -pixels * 2))
    layout(child.width, constraints.constrainHeight(child.height + pixels * 2)) { child.placeRelative(0, pixels) }
}
