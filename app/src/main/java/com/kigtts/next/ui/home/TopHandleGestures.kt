package com.kigtts.next.ui.home

import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.*

/** Keep pickup and resizing in one pointer owner, including the initial movement past touch slop. */
suspend fun PointerInputScope.topHandleGestures(press: () -> Unit, release: () -> Unit,
    start: () -> Unit, move: (Float) -> Unit, end: () -> Unit, cancel: () -> Unit, horizontal: Boolean = false) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        press()
        var dragging = false
        try {
            val activated = if (horizontal) awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                else awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (activated != null) {
                dragging = true
                start()
                move(if (horizontal) activated.position.x - down.position.x else activated.position.y - down.position.y)
                // Once picked up, retain the handle before moving neighbouring content sees Main.
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == activated.id }
                    if (change == null) { cancel(); break }
                    if (!change.pressed) {
                        if (change.isConsumed) cancel() else { change.consume(); end() }
                        break
                    }
                    move(if (horizontal) change.positionChange().x else change.positionChange().y); change.consume()
                }
                dragging = false
            }
        } finally {
            if (dragging) cancel()
            release()
        }
    }
}
