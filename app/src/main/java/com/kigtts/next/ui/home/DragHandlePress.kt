package com.kigtts.next.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.kigtts.next.ui.components.DragFeedback

@Stable
class DragHandlePress(private val state: State<Boolean>, val modifier: Modifier) {
    val pressed: Boolean get() = state.value
}

/** Observe pickup without consuming events or bypassing the native drag threshold. */
@Composable
fun rememberDragHandlePress(feedback: DragFeedback, value: () -> Float, dragging: () -> Boolean,
    feedbackOnPress: Boolean = true): DragHandlePress {
    val pressed = remember { mutableStateOf(false) }
    val latestFeedback by rememberUpdatedState(feedback)
    val latestValue by rememberUpdatedState(value)
    val latestDragging by rememberUpdatedState(dragging)
    val latestEnabled by rememberUpdatedState(feedbackOnPress)
    val modifier = remember(pressed) { Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed.value = true
            var released = false
            try {
                if (latestEnabled) latestFeedback.start(latestValue())
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (change == null) break
                    if (!change.pressed) { released = !change.isConsumed; break }
                }
            } finally {
                pressed.value = false
                // A real drag owns its completion/cancellation feedback. A tap only gets pickup.
                if (!released || !latestDragging()) latestFeedback.finish(cancelled = true)
            }
        }
    } }
    return remember(pressed, modifier) { DragHandlePress(pressed, modifier) }
}
