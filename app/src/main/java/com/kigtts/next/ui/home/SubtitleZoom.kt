package com.kigtts.next.ui.home

import androidx.compose.foundation.gestures.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.subtitleZoom(key: Any, onZoomEnd: () -> Unit = {}, scale: (Float) -> Unit) = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoomed = false
        var pinching = false
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    pinching = true
                    val zoom = event.calculateZoom()
                    if (zoom.isFinite() && zoom > 0f && zoom != 1f) { zoomed = true; scale(zoom) }
                }
                if (pinching) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        } finally { if (zoomed) onZoomEnd() }
    }
}
