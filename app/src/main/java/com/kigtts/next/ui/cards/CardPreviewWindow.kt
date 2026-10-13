@file:OptIn(androidx.compose.animation.core.ExperimentalTransitionApi::class)
package com.kigtts.next.ui.cards

import android.view.ViewGroup
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.window.*
import com.kigtts.next.ui.components.DialogMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** A transparent full-screen window keeps the paper and its actions through predictive exit. */
@Composable
internal fun CardPreviewWindow(onDismiss: () -> Unit, enterReady: Boolean = true,
    printHandoff: Boolean = false, closeEnabled: Boolean = true, initiallyOpened: Boolean = false,
    content: @Composable (reveal: Float, closing: Boolean, close: (() -> Unit) -> Unit) -> Unit) {
    var opened by rememberSaveable { mutableStateOf(initiallyOpened) }
    var closing by rememberSaveable { mutableStateOf(false) }
    // The previous native window already owns its accepted close action. Recreating that
    // exiting window must not resurrect an opened Dialog before the parent removes it.
    val restoredClosing = remember { closing }
    if (restoredClosing) return
    val state = remember { SeekableTransitionState(initiallyOpened || opened) }
    val transition = rememberTransition(state, "card-preview-window")
    val scope = rememberCoroutineScope()
    val acceptedExit = remember { CardPreviewExit() }
    DisposableEffect(acceptedExit) { onDispose { acceptedExit.finish() } }
    val dismiss by rememberUpdatedState(onDismiss)
    fun close(action: () -> Unit) {
        if (closing || !closeEnabled) return
        closing = true
        acceptedExit.accept(action)
        // Once accepted, rotation/disposal may shorten the animation but must not lose the action.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { state.animateTo(false) } finally { acceptedExit.finish() }
        }
    }
    LaunchedEffect(enterReady) { if (enterReady && !opened) { opened = true; withFrameNanos { }; state.animateTo(true) } }
    val reveal by transition.animateFloat({
        if (printHandoff && targetState) tween(460, easing = FastOutSlowInEasing) else DialogMotion.compact(targetState)
    }, label = "card-preview-reveal") { if (it) 1f else 0f }
    val dim by transition.animateFloat({ tween(if (targetState) 180 else 140) }, label = "card-preview-dim") { if (it) .6f else 0f }
    Dialog({ close { dismiss() } }, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setWindowAnimations(0); setDimAmount(0f)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            onDispose { }
        }
        PredictiveBackHandler(!closing && closeEnabled) { events ->
            try { events.collect { state.seekTo(it.progress, false) }; close { dismiss() } }
            catch (cancel: CancellationException) { if (!closing) scope.launch { state.animateTo(true) }; throw cancel }
        }
        Box(Modifier.fillMaxSize().testTag("card-preview-window")) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(MaterialTheme.colorScheme.scrim)
                .clickable(enabled = !closing && closeEnabled, interactionSource = null, indication = null) { close { dismiss() } }
                .testTag("card-preview-scrim").clearAndSetSemantics { })
            content(reveal, closing, ::close)
        }
    }
}
