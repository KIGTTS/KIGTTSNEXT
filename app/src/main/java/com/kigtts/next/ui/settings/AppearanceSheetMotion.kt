package com.kigtts.next.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.*

/** One offset owner for the handle and nested content; every new touch interrupts settling. */
@Stable internal class AppearanceSheetMotion(private val scope: CoroutineScope,
    private val range: State<Float>, private val density: Float, initialExpanded: Boolean,
    private val rememberExpanded: (Boolean) -> Unit) : NestedScrollConnection {
    var fraction by mutableFloatStateOf(if (initialExpanded) 0f else 1f)
        private set
    var animating by mutableStateOf(false)
        private set
    private var settling: Job? = null
    val offset get() = fraction * range.value
    fun interrupt() { settling?.cancel(); settling = null; animating = false }
    fun drag(delta: Float): Float {
        interrupt()
        val length = range.value.coerceAtLeast(1f)
        val old = fraction
        fraction = (fraction + delta / length).coerceIn(0f, 1f)
        return (fraction - old) * length
    }
    fun expand() = moveTo(0f)
    fun toggle() = moveTo(if (fraction < .5f) 1f else 0f)
    fun settle(velocity: Float) = moveTo(when {
        velocity < -200f * density -> 0f
        velocity > 200f * density -> 1f
        fraction < .5f -> 0f
        else -> 1f
    })
    private fun moveTo(target: Float) {
        interrupt(); rememberExpanded(target == 0f)
        if (kotlin.math.abs(fraction - target) < .001f) { fraction = target; return }
        settling = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animating = true
            try { animate(fraction, target, animationSpec = tween(240, easing = FastOutSlowInEasing)) { value, _ -> fraction = value } }
            finally { animating = false }
        }
    }
    override fun onPreScroll(available: Offset, source: NestedScrollSource) =
        if (source == NestedScrollSource.UserInput && available.y < 0f) Offset(0f, drag(available.y)) else Offset.Zero
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) =
        if (source == NestedScrollSource.UserInput && available.y > 0f) Offset(0f, drag(available.y)) else Offset.Zero
    override suspend fun onPreFling(available: Velocity): Velocity {
        if (available.y < 0f && fraction > .001f) { settle(available.y); return Velocity(0f, available.y) }
        return Velocity.Zero
    }
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // A pre-fling already owns settling; the child's zero remainder must not reverse it.
        if (!animating) settle(available.y)
        return Velocity(0f, available.y)
    }
}

/** Observe without consuming the down so buttons, sliders and content retain their own gestures. */
internal fun Modifier.interruptAppearanceSheet(motion: AppearanceSheetMotion) = pointerInput(motion) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        motion.interrupt()
        do { val event = awaitPointerEvent(PointerEventPass.Final) } while (event.changes.any { it.pressed })
    }
}
