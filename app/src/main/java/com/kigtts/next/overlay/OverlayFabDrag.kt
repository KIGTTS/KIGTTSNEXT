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

/** Claim FAB drags before the expanded dial's scrolling, keeping stationary holds for settings. */
@Composable
internal fun Modifier.overlayFabDrag(move: (Float, Float) -> Unit, drop: () -> Unit, interact: () -> Unit): Modifier {
    val onMove by rememberUpdatedState(move)
    val onDrop by rememberUpdatedState(drop)
    val onInteract by rememberUpdatedState(interact)
    val feedback = LocalHapticFeedback.current
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(view) {
        val location = IntArray(2)
        fun screen(point: Offset): Offset {
            view.getLocationOnScreen(location)
            return point + origin + Offset(location[0].toFloat(), location[1].toFloat())
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val start = screen(down.position)
            var previous = start
            var dragging = false
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) { if (dragging) change.consume(); break }
                    val point = screen(change.position)
                    if (!dragging) {
                        if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis || change.isConsumed) break
                        if ((point - start).getDistance() < viewConfiguration.touchSlop) continue
                        dragging = true
                        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onInteract()
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
