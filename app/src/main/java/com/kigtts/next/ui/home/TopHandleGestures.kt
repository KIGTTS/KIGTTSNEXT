package com.kigtts.next.ui.home

import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.*

/** Keep pickup and resizing in one pointer owner, including the initial movement past touch slop. */
suspend fun PointerInputScope.topHandleGestures(press: () -> Unit, release: () -> Unit,
    start: () -> Unit, move: (Float) -> Unit, end: () -> Unit, cancel: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        press()
        var dragging = false
        try {
            val activated = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (activated != null) {
                dragging = true
                start()
                move(activated.position.y - down.position.y)
                // Once picked up, retain the handle before moving neighbouring content sees Main.
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == activated.id }
                    if (change == null) { cancel(); break }
                    if (!change.pressed) {
                        if (change.isConsumed) cancel() else { change.consume(); end() }
                        break
                    }
                    move(change.positionChange().y); change.consume()
                }
                dragging = false
            }
        } finally {
            if (dragging) cancel()
            release()
        }
    }
}
