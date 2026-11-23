package com.kigtts.next.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ReturnProgress(val progress: Float, val edge: Int)
internal val IdleReturnProgress: State<ReturnProgress> = mutableStateOf(ReturnProgress(0f, 0))

private class ReturnSettlement { var job: Job? = null }

@Composable
fun rememberPredictiveReturn(enabled: Boolean, identity: Any? = null,
    handoff: (ReturnProgress) -> Boolean = { false }, complete: () -> Unit): State<ReturnProgress> {
    val progress = remember { mutableFloatStateOf(0f) }
    var edge by remember { mutableIntStateOf(0) }
    val latestComplete by rememberUpdatedState(complete)
    val latestHandoff by rememberUpdatedState(handoff)
    val latestIdentity by rememberUpdatedState(identity)
    var owner by remember { mutableStateOf(identity) }
    val scope = rememberCoroutineScope()
    val settling = remember { ReturnSettlement() }
    SideEffect { if (owner != identity) progress.floatValue = 0f }
    PredictiveBackHandler(enabled) { events ->
        settling.job?.cancel()
        val startedOn = latestIdentity
        owner = startedOn
        progress.floatValue = 0f
        try {
            events.collect { progress.floatValue = it.progress; edge = it.swipeEdge }
            var handedOff = false
            if (latestIdentity == startedOn) {
                val pose = ReturnProgress(progress.floatValue, edge)
                latestComplete()
                handedOff = pose.progress > 0f && latestHandoff(pose)
            }
            if (!handedOff) settling.job = scope.launch {
                // Keep the local preview through the update for in-page actions.
                withFrameNanos { }
                progress.floatValue = 0f
            }
        } catch (cancel: CancellationException) {
            settling.job = scope.launch {
                Animatable(progress.floatValue).animateTo(0f, spring(.9f, 600f)) {
                    progress.floatValue = value
                }
            }
            throw cancel
        }
    }
    // Callers read this from layer blocks, keeping pointer progress out of composition.
    return remember { derivedStateOf { ReturnProgress(if (owner == latestIdentity) progress.floatValue else 0f, edge) } }
}

fun Modifier.predictiveReturn(back: State<ReturnProgress>, edgeOverride: Int? = null) = graphicsLayer {
    val (progress, swipeEdge) = back.value
    val edge = edgeOverride ?: swipeEdge
    scaleX = 1f - .06f * progress
    scaleY = 1f - .06f * progress
    translationX = 28.dp.toPx() * progress * if (edge == 0) 1 else -1
    shape = if (progress > 0f) RoundedCornerShape((28f * progress).dp) else RectangleShape
    clip = progress > 0f
}
