package com.kigtts.next.ui.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs

internal data class CameraPullOrigin(val mode: CameraPullMode, val parent: String, val camera: String? = null)

/** Central title/viewfinder regions exclude album rails, FABs, shutter/zoom controls and system edges. */
internal fun cameraPullSafeStart(x: Float, y: Float, width: Float, height: Float, density: Float): Boolean {
    if (width <= 0 || height <= 0 || y < 32f * density) return false
    return x in width * .23f..width * .73f && y < height * .68f
}

@Composable
internal fun Modifier.cameraPullNavigation(state: CameraPullState, origin: () -> CameraPullOrigin?): Modifier {
    val latest = rememberUpdatedState(origin)
    return onGloballyPositioned { state.rootCoordinates = it }.pointerInput(Unit) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val candidate = latest.value() ?: return@awaitEachGesture
            if (!state.acceptingGesture || state.blocked || state.excludedStart(first.position) || !cameraPullSafeStart(first.position.x, first.position.y,
                    size.width.toFloat(), size.height.toFloat(), density)) return@awaitEachGesture
            val nativeViewfinder = candidate.mode == CameraPullMode.EXIT && state.nativeViewfinderStart(first.position)
            val paused = state.pauseSettlement()
            val velocity = VelocityTracker().apply { addPosition(first.uptimeMillis, first.position) }
            var claimed = false; var released = false; var cancelled = false
            var dx = 0f; var dy = 0f
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == first.id } ?: break
                    if (event.changes.count { it.pressed } > 1) { cancelled = true; break }
                    velocity.addPosition(change.uptimeMillis, change.position)
                    val delta = change.position - change.previousPosition
                    if (!claimed) {
                        // Only an explicitly registered Android viewfinder may yield native consumption;
                        // exposure/zoom controls and every other native slider keep their gesture.
                        if (!nativeViewfinder && change.isConsumed && (delta.x != 0f || delta.y != 0f)) break
                        dx += delta.x; dy += delta.y
                        if (abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) break
                        val directed = if (candidate.mode == CameraPullMode.ENTER) dy else -dy
                        if (directed > viewConfiguration.touchSlop && abs(dy) > abs(dx) * 1.2f) {
                            claimed = state.begin(candidate.mode, candidate.parent, candidate.camera, size.height.toFloat())
                            if (!claimed) break
                            state.drag(dy)
                        }
                    } else state.drag(delta.y)
                    if (claimed) event.changes.forEach { it.consume() }
                    released = !change.pressed
                } while (!released)
            } finally {
                if (claimed) state.finish(cancelled || !released, if (released) velocity.calculateVelocity().y else 0f)
                else if (paused) state.resumeSettlement()
            }
        }
    }
}
