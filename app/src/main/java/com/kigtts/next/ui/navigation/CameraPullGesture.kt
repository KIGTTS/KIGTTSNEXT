package com.kigtts.next.ui.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs

internal data class CameraPullOrigin(val mode: CameraPullMode, val parent: String, val camera: String? = null,
    val horizontal: Boolean = false)

/** Central title/viewfinder regions exclude album rails, FABs, shutter/zoom controls and system edges. */
internal fun cameraPullSafeStart(x: Float, y: Float, width: Float, height: Float, density: Float): Boolean {
    if (width <= 0 || height <= 0 || y < 32f * density) return false
    return x in width * .23f..width * .73f && y < height * .68f
}

@Composable
internal fun Modifier.cameraPullNavigation(state: CameraPullState, origin: () -> CameraPullOrigin?): Modifier {
    val latest = rememberUpdatedState(origin)
    val exclusions = LocalHomeSwipeExclusions.current
    return onGloballyPositioned { state.rootCoordinates = it }.pointerInput(Unit) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val candidate = latest.value() ?: return@awaitEachGesture
            if (candidate.horizontal && (first.position.x < 24 * density || first.position.x > size.width - 24 * density ||
                    candidate.mode == CameraPullMode.ENTER && exclusions?.contains(first.position) == true)) return@awaitEachGesture
            val entry = candidate.mode == CameraPullMode.ENTER && state.entryStart(first.position)
            if (!state.acceptingGesture || state.blocked || state.excludedStart(first.position) || !entry && !candidate.horizontal && !cameraPullSafeStart(first.position.x, first.position.y,
                    size.width.toFloat(), size.height.toFloat(), density)) return@awaitEachGesture
            val nativeViewfinder = candidate.mode == CameraPullMode.EXIT && state.nativeViewfinderStart(first.position)
            val paused = state.pauseSettlement()
            val velocity = VelocityTracker().apply { addPosition(first.uptimeMillis, first.position) }
            var claimed = false; var released = false; var cancelled = false
            var dx = 0f; var dy = 0f
            try {
                do {
                    // Use the same child-first rule as homeSideDrawer: pagers, subtitle
                    // input and sliders keep consumed drags. Only a registered heading
                    // takes priority over its app bar; an already claimed pull stays owned.
                    val event = awaitPointerEvent(if (entry || claimed) PointerEventPass.Initial else PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == first.id } ?: break
                    if (event.changes.count { it.pressed } > 1) { cancelled = true; break }
                    velocity.addPosition(change.uptimeMillis, change.position)
                    val delta = change.position - change.previousPosition
                    if (!claimed) {
                        // Only an explicitly registered Android viewfinder may yield native consumption;
                        // exposure/zoom controls and every other native slider keep their gesture.
                        if (!entry && !nativeViewfinder && change.isConsumed && (delta.x != 0f || delta.y != 0f)) break
                        dx += delta.x; dy += delta.y
                        val distance = if (candidate.horizontal) -dx else dy
                        val cross = if (candidate.horizontal) dy else dx
                        if (abs(cross) > viewConfiguration.touchSlop && abs(cross) > abs(distance)) break
                        val directed = if (candidate.mode == CameraPullMode.ENTER) distance else -distance
                        if (directed > viewConfiguration.touchSlop && abs(distance) > abs(cross) * 1.2f) {
                            claimed = state.begin(candidate.mode, candidate.parent, candidate.camera,
                                (if (candidate.horizontal) size.width else size.height).toFloat(), candidate.horizontal)
                            if (!claimed) break
                            state.drag(distance)
                        }
                    } else state.drag(if (candidate.horizontal) -delta.x else delta.y)
                    if (claimed) event.changes.forEach { it.consume() }
                    released = !change.pressed
                } while (!released)
            } finally {
                if (claimed) state.finish(cancelled || !released, if (released) {
                    val speed = velocity.calculateVelocity()
                    if (candidate.horizontal) -speed.x else speed.y
                } else 0f)
                else if (paused) state.resumeSettlement()
            }
        }
    }
}
