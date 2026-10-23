package com.kigtts.next.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Cursor motion does not invalidate text layout or restart its breathing on every keystroke. */
@Composable
internal fun SubtitleCursorMotion(frame: SubtitleGlyphFrame?, offset: Int, color: Color, modifier: Modifier) {
    if (frame == null) return
    val box = frame.layout.getCursorRect(offset.coerceIn(0, frame.layout.layoutInput.text.length)).translate(frame.origin)
    // A new viewport or font is direct manipulation: initialize at the newly
    // measured caret immediately, while ordinary typing keeps its short movement.
    val position = key(frame.viewport, frame.layout.layoutInput.constraints, frame.layout.layoutInput.style) {
        animateOffsetAsState(box.topLeft, tween(95, easing = FastOutSlowInEasing), label = "subtitle-caret-position")
    }
    val breathing = rememberInfiniteTransition(label = "subtitle-caret-breath").animateFloat(.18f, 1f,
        infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "subtitle-caret-opacity")
    Canvas(modifier) {
        drawRoundRect(color.copy(alpha = if (position.value != box.topLeft) 1f else breathing.value),
            topLeft = position.value, size = Size(3.dp.toPx(), box.height), cornerRadius = CornerRadius(1.5.dp.toPx()))
    }
}
