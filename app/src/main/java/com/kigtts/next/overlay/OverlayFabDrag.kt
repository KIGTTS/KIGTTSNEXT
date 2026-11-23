package com.kigtts.next.overlay

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.ExperimentalComposeUiApi

/** Only the collapsed ball can move; stationary holds still open settings. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.overlayFabDrag(move: (Float, Float) -> Unit, drop: () -> Unit, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val onMove by rememberUpdatedState(move)
    val onDrop by rememberUpdatedState(drop)
    val feedback = LocalHapticFeedback.current
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val raw = remember { OverlayRawPointer() }
    return onGloballyPositioned { origin = it.positionInRoot() }.motionEventSpy { event ->
        raw.time = event.eventTime
        raw.position = Offset(event.rawX, event.rawY)
    }.pointerInput(view) {
        val location = IntArray(2)
        fun screen(point: Offset, time: Long): Offset {
            // Native raw coordinates belong to the event's window position. Asking the
            // view for its *current* location mixes two frames while WindowManager moves.
            if (raw.time == time) return raw.position
            view.getLocationOnScreen(location)
            return point + origin + Offset(location[0].toFloat(), location[1].toFloat())
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val start = screen(down.position, down.uptimeMillis)
            var previous = start
            var dragging = false
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) { if (dragging) change.consume(); break }
                    val point = screen(change.position, change.uptimeMillis)
                    if (!dragging) {
                        if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis || change.isConsumed) break
                        if ((point - start).getDistance() < viewConfiguration.touchSlop) continue
                        dragging = true
                        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    change.consume()
                    val amount = point - previous
                    previous = point
                    onMove(amount.x, amount.y)
                }
            } finally { if (dragging) onDrop() }
        }
    }
}

private class OverlayRawPointer {
    var time = Long.MIN_VALUE
    var position = Offset.Zero
}
