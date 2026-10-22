package com.kigtts.next.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.kigtts.next.state.SidePage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

@Stable
internal class HomeSideDrawer(private val scope: CoroutineScope, private val open: (SidePage) -> Unit,
    private val close: () -> Unit) {
    var page by mutableStateOf<SidePage?>(null); private set
    var visible by mutableFloatStateOf(0f); private set
    private var extent = 1f
    private var settling = false
    val active get() = page != null
    val direction get() = if (page == SidePage.SKETCH) -1f else 1f
    fun begin(delta: Float, width: Float): Boolean {
        if (active || width <= 0f) return false
        page = if (delta > 0f) SidePage.SKETCH else SidePage.CARDS
        extent = width; visible = 0f; open(requireNotNull(page)); drag(delta)
        return true
    }
    fun drag(delta: Float) { if (!settling) visible = (visible - delta * direction / extent).coerceIn(0f, 1f) }
    fun finish(cancelled: Boolean, velocity: Float = 0f) {
        if (!active || settling) return
        settling = true
        val keep = !cancelled && (visible >= .28f || visible > .06f && -velocity * direction > 700f)
        if (!keep) close()
        scope.launch {
            try {
                Animatable(visible).animateTo(if (keep) 1f else 0f,
                    tween((90 + 180 * abs((if (keep) 1f else 0f) - visible)).toInt(), easing = FastOutSlowInEasing)) { visible = value }
            } finally { page = null; settling = false }
        }
    }
}

class SideDrawerPose internal constructor(private val state: HomeSideDrawer, private val side: Boolean) {
    val position get() = if (side) state.direction * (1f - state.visible) else -state.direction * .08f * state.visible
}

/** Let text selection, media gestures and scrolling children claim first. */
@Composable
internal fun Modifier.homeSideDrawer(state: HomeSideDrawer, available: () -> Boolean): Modifier {
    val latest by rememberUpdatedState(available)
    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!latest() || state.active || down.position.x < 24 * density || down.position.x > size.width - 24 * density) return@awaitEachGesture
            val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var dx = 0f; var dy = 0f; var claimed = false; var released = false
            try {
                do {
                    val event = awaitPointerEvent(if (claimed) PointerEventPass.Initial else PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.count { it.pressed } > 1) break
                    val delta = change.positionChange()
                    velocity.addPosition(change.uptimeMillis, change.position)
                    if (!claimed) {
                        if (change.isConsumed) break
                        dx += delta.x; dy += delta.y
                        if (abs(dy) > viewConfiguration.touchSlop && abs(dy) >= abs(dx)) break
                        if (abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy) * 1.2f) {
                            if (!latest()) break
                            claimed = state.begin(dx, size.width.toFloat())
                        }
                    } else state.drag(delta.x)
                    if (claimed) event.changes.forEach { it.consume() }
                    released = !change.pressed
                } while (!released)
            } finally { if (claimed) state.finish(!released, if (released) velocity.calculateVelocity().x else 0f) }
        }
    }
}
