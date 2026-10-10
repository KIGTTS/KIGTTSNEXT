@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.animation.core.ExperimentalTransitionApi::class)
package com.kigtts.next.ui.home

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.QuickPhrase
import com.kigtts.next.ui.components.CornerNeighbors
import com.kigtts.next.ui.components.LocalCornerRadius
import com.kigtts.next.ui.components.DialogMotion
import com.kigtts.next.ui.components.DialogFormMotion
import com.kigtts.next.ui.components.LocalDialogFormMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class PhraseDialogAnchor(val bounds: Rect, val neighbors: CornerNeighbors = CornerNeighbors())

/** Both bounds live in the dialog window; the small end is a snapshot of the tapped list card. */
@Composable
internal fun PhraseDialogTransform(anchor: PhraseDialogAnchor?, source: QuickPhrase?, restored: Boolean,
    dismiss: () -> Unit, repository: com.kigtts.next.media.MediaRepository? = null,
    content: @Composable (close: (() -> Unit) -> Unit, enabled: Boolean) -> Unit) {
    val state = remember { SeekableTransitionState(restored) }
    val transition = rememberTransition(state, label = "phrase-container-transform")
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val latestDismiss by rememberUpdatedState(dismiss)
    fun close(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch { state.animateTo(false); action() }
    }
    LaunchedEffect(Unit) { if (!restored) { withFrameNanos { }; state.animateTo(true) } }
    PredictiveBackHandler(!closing) { events ->
        try {
            events.collect { state.seekTo(it.progress, targetState = false) }
            close { latestDismiss() }
        } catch (cancel: CancellationException) {
            if (!closing) scope.launch { state.animateTo(true) }
            throw cancel
        }
    }
    val fraction by transition.animateFloat(transitionSpec = { DialogMotion.container(targetState) }, label = "container-shape") {
        if (it) 1f else 0f
    }
    val expandedColor = MaterialTheme.colorScheme.background
    val collapsedColor = source?.let { quickPhraseColor(it) } ?: MaterialTheme.colorScheme.surfaceContainer
    val background by transition.animateColor(transitionSpec = { DialogMotion.container(targetState) }, label = "container-color") {
        if (it) expandedColor else collapsedColor
    }
    val body by transition.animateFloat({ DialogMotion.content(targetState) }, label = "form-body") { if (it) 1f else 0f }
    val header by transition.animateFloat({ tween(if (targetState) 180 else 100, delayMillis = if (targetState) 30 else 0) }, label = "form-header") { if (it) 1f else 0f }
    val sourceOpacity by transition.animateFloat({ DialogMotion.origin(targetState) }, label = "origin-content") { if (it) 0f else 1f }
    val neighbors = anchor?.neighbors ?: CornerNeighbors()
    val outer = LocalCornerRadius.current.coerceIn(4.dp, 20.dp)
    val inner = (LocalCornerRadius.current * .22f).coerceIn(4.dp, outer)
    fun corner(connected: Boolean) = (if (connected) inner else outer) * (1f - fraction.coerceIn(0f, 1f))
    val shape = RoundedCornerShape(corner(neighbors.start || neighbors.above), corner(neighbors.end || neighbors.above),
        corner(neighbors.end || neighbors.below), corner(neighbors.start || neighbors.below))
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    SharedTransitionLayout(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = .32f * fraction }.background(MaterialTheme.colorScheme.scrim))
        transition.AnimatedContent(Modifier.fillMaxSize(), transitionSpec = {
            EnterTransition.None.togetherWith(ExitTransition.None).using(null)
        }) { expanded ->
            val shared = Modifier.sharedBounds(rememberSharedContentState("phrase-dialog-container"), this,
                enter = EnterTransition.None, exit = ExitTransition.None,
                boundsTransform = { _, _ -> DialogMotion.container(transition.targetState) },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = OverlayClip(shape))
            if (expanded) Surface(shared.fillMaxSize().testTag("phrase-dialog-container"), shape = shape, color = background) {
                Box(Modifier.skipToLookaheadSize()) {
                    CompositionLocalProvider(LocalDialogFormMotion provides DialogFormMotion(header, body)) {
                        content({ action -> close(action) }, !closing && state.currentState && !isTransitionActive)
                    }
                }
            } else Box(Modifier.fillMaxSize()) {
                val bounds = anchor?.bounds?.translate(-origin)
                val position = bounds?.topLeft ?: Offset.Zero
                val small = Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                    .then(if (bounds != null) Modifier.size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                        else Modifier.width(180.dp).height(QuickPhraseHeight))
                    .then(shared).clearAndSetSemantics { testTag = "phrase-dialog-origin" }
                Surface(small, shape = shape, color = background) {
                    Box(Modifier.skipToLookaheadSize().graphicsLayer { alpha = sourceOpacity }) {
                        if (source != null) EditablePhraseCard(source, {}, {}, neighbors, Modifier.fillMaxSize(), repository = repository)
                        else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text("添加快捷文本", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
