package com.kigtts.next.ui.album

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Observe the second finger before the grid consumes a drag. Single-finger scroll and taps stay native. */
internal fun Modifier.albumGridZoom(onStep: (shrink: Boolean, centroid: Offset) -> Boolean): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            var stopped = false
            var scale = 1f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    pinching = true
                    scale *= event.calculateZoom()
                    if (!stopped && (scale <= .78f || scale >= 1.28f)) {
                        stopped = onStep(scale < 1f, event.calculateCentroid(useCurrent = true))
                        scale = 1f
                    }
                }
                if (pinching) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
