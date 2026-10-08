package com.kigtts.next.ui.home

import androidx.compose.foundation.gestures.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.subtitleZoom(key: Any, scale: (Float) -> Unit) = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom.isFinite() && zoom != 1f) scale(zoom)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
