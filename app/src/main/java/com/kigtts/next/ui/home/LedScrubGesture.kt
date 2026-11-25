package com.kigtts.next.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.kigtts.next.state.LedPlayback
import kotlin.math.abs

internal fun Modifier.ledScrubGesture(playback: LedPlayback, enabled: Boolean, reducedMotion: Boolean): Modifier {
    if (!enabled) return this
    return pointerInput(playback, reducedMotion) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tracker = VelocityTracker().apply { addPosition(first.uptimeMillis, first.position) }
            var claimed = false
            var released = false
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) break
                    val change = event.changes.firstOrNull { it.id == first.id } ?: break
                    if (change.isConsumed && !claimed) break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (claimed) {
                        playback.scrub(change.position.x - change.previousPosition.x)
                    } else if (change.pressed) {
                        val delta = change.position - first.position
                        val slop = viewConfiguration.touchSlop
                        if (abs(delta.y) > slop && abs(delta.y) > abs(delta.x)) break
                        if (abs(delta.x) > slop && abs(delta.x) > abs(delta.y)) {
                            claimed = playback.startScrub()
                            if (!claimed) break
                            playback.scrub(delta.x)
                        }
                    }
                    if (claimed) change.consume()
                    released = !change.pressed
                } while (!released)
            } finally {
                if (claimed) playback.finishScrub(if (released && !reducedMotion) tracker.calculateVelocity().x else 0f)
            }
        }
    }
}
