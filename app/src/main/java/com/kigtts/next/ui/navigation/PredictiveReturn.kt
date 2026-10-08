package com.kigtts.next.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

data class ReturnProgress(val progress: Float, val edge: Int)

@Composable
fun rememberPredictiveReturn(enabled: Boolean, identity: Any? = null, complete: () -> Unit): ReturnProgress {
    var target by remember { mutableFloatStateOf(0f) }
    var gesturing by remember { mutableStateOf(false) }
    var edge by remember { mutableIntStateOf(0) }
    val latestComplete by rememberUpdatedState(complete)
    val latestIdentity by rememberUpdatedState(identity)
    var owner by remember { mutableStateOf(identity) }
    val progress by animateFloatAsState(target, if (gesturing) snap() else spring(.9f, 600f), label = "predictive-return")
    PredictiveBackHandler(enabled) { events ->
        val startedOn = latestIdentity
        owner = startedOn
        gesturing = true
        try {
            events.collect { target = it.progress; edge = it.swipeEdge }
            if (latestIdentity == startedOn) latestComplete()
        } catch (cancel: CancellationException) {
            throw cancel
        } finally {
            target = 0f
            gesturing = false
        }
    }
    return ReturnProgress(if (owner == identity) progress else 0f, edge)
}

fun Modifier.predictiveReturn(progress: Float, edge: Int) = graphicsLayer {
    scaleX = 1f - .06f * progress
    scaleY = 1f - .06f * progress
    translationX = 28.dp.toPx() * progress * if (edge == 0) 1 else -1
    shape = RoundedCornerShape((28f * progress).dp)
    clip = progress > 0f
}
