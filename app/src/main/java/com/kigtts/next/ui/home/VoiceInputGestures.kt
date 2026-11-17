package com.kigtts.next.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.kigtts.next.state.VoiceInputGestureState

/** The page root remains the pointer owner even after a long press reveals an overlay. */
internal fun Modifier.trackVoiceInputGesture(gesture: VoiceInputGestureState, onRelease: () -> Unit): Modifier = pointerInput(gesture) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        gesture.down(down.position, viewConfiguration.touchSlop)
        var released = false
        try {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                gesture.move(change.position)
                if (gesture.armed) change.consume()
                if (!change.pressed) { gesture.release(); released = true; onRelease(); break }
            }
        } finally { if (!released) gesture.cancel() }
    }
}
