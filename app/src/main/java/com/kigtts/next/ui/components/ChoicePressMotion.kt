package com.kigtts.next.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import kotlinx.coroutines.*

/** Observe down before scrollable click delay. A short tap completes one visible, interruptible pulse. */
internal class ChoicePressMotion(private val scope: CoroutineScope, private val feedback: () -> Unit) {
    val progress = Animatable(0f)
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var generation = 0
    private var pointerFeedback = false
    private var releasedAt = 0L
    fun press() {
        generation++; releaseJob?.cancel(); pressJob?.cancel()
        pointerFeedback = true; releasedAt = 0L; feedback()
        pressJob = scope.launch { progress.animateTo(1f, tween(75, easing = FastOutSlowInEasing)) }
    }
    fun release(cancelled: Boolean) {
        val expected = generation; val started = pressJob
        releasedAt = SystemClock.uptimeMillis()
        if (cancelled) { pointerFeedback = false; pressJob?.cancel() }
        releaseJob?.cancel()
        releaseJob = scope.launch {
            if (!cancelled) started?.join()
            if (generation == expected) progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 500f))
        }
    }
    fun commit(action: () -> Unit) {
        if (!pointerFeedback || releasedAt != 0L && SystemClock.uptimeMillis() - releasedAt > 250) { press(); release(false) }
        pointerFeedback = false; action()
    }
}

@Composable
internal fun rememberChoicePressMotion(): ChoicePressMotion {
    val scope = rememberCoroutineScope(); val feedback by rememberUpdatedState(feedbackClick {})
    return remember(scope) { ChoicePressMotion(scope) { feedback() } }
}

internal fun Modifier.choicePressFeedback(motion: ChoicePressMotion) = pointerInput(motion) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        motion.press(); var cancelled = false
        try {
            do {
                val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: break
                // Clickable consumes the initial down too. Only a consumed move indicates scroll takeover.
                if (change.pressed && change.previousPressed && change.position != change.previousPosition && change.isConsumed && !cancelled) { cancelled = true; motion.release(true) }
            } while (change.pressed)
        } finally { if (!cancelled) motion.release(false) }
    }
}
