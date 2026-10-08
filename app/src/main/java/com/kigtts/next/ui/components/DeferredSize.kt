package com.kigtts.next.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.constrainWidth

/** Fast-changing sizes are observed during measure, rather than restarting composition. */
fun Modifier.heightFrom(height: () -> Float) = layout { measurable, constraints ->
    val pixels = height().coerceAtLeast(0f).dp.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
    val child = measurable.measure(constraints.copy(minHeight = pixels, maxHeight = pixels))
    layout(child.width, child.height) { child.placeRelative(0, 0) }
}

fun Modifier.horizontalPaddingFrom(padding: () -> Float) = layout { measurable, constraints ->
    val pixels = padding().coerceAtLeast(0f).dp.roundToPx()
    val child = measurable.measure(constraints.offset(horizontal = -pixels * 2))
    layout(constraints.constrainWidth(child.width + pixels * 2), child.height) { child.placeRelative(pixels, 0) }
}
