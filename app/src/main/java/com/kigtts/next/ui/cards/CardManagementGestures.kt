package com.kigtts.next.ui.cards

import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*

/** Observe native button presses without stealing ordinary clicks or a scrolling gesture. */
@Composable internal fun Modifier.cardManagementLongPress(id: String, enabled: Boolean, manage: () -> Unit): Modifier {
    val latest = rememberUpdatedState(manage)
    return if (!enabled) this else pointerInput(id) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            awaitPointerEvent(PointerEventPass.Main)
            if (awaitLongPressOrCancellation(down.id) != null) {
                latest.value()
                do { val event = awaitPointerEvent(PointerEventPass.Initial); event.changes.forEach { it.consume() } }
                while (event.changes.any { it.pressed })
            }
        }
    }
}
