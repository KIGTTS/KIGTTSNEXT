@file:OptIn(androidx.compose.animation.core.ExperimentalTransitionApi::class)
package com.kigtts.next.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private data class DialogActions(val isClosing: () -> Boolean, val close: (() -> Unit) -> Unit)
private val LocalDialogActions = compositionLocalOf<DialogActions?> { null }

/** Own the window until the exit completes, including button, scrim and predictive-back dismissal. */
@Composable
fun MotionAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null, dismissEnabled: Boolean = true) {
    var opened by rememberSaveable { mutableStateOf(false) }
    val state = remember { SeekableTransitionState(opened) }
    val transition = rememberTransition(state, "alert-dialog")
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismissRequest)
    val keyboard = LocalSoftwareKeyboardController.current
    val modalWindows = LocalModalWindows.current
    DisposableEffect(modalWindows) {
        modalWindows?.enter()
        onDispose { modalWindows?.leave() }
    }
    fun close(action: () -> Unit) {
        if (closing) return
        closing = true; keyboard?.hide()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { state.animateTo(false) } finally { action() }
        }
    }
    LaunchedEffect(Unit) { if (!opened) { opened = true; withFrameNanos { }; state.animateTo(true) } }
    val reveal by transition.animateFloat({ DialogMotion.compact(targetState) }, label = "alert-size") { if (it) 1f else 0f }
    val opacity by transition.animateFloat({ tween(if (targetState) 150 else 100) }, label = "alert-opacity") { if (it) 1f else 0f }
    val dim by transition.animateFloat({ tween(if (targetState) 180 else 140) }, label = "alert-dim") { if (it) .32f else 0f }
    val body by transition.animateFloat({ tween(if (targetState) 150 else 80, delayMillis = if (targetState) 40 else 0) }, label = "alert-content") { if (it) 1f else 0f }
    Dialog({ if (dismissEnabled) close { dismiss() } }, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, dismissOnBackPress = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.apply { setWindowAnimations(0); setDimAmount(0f) }
            onDispose { }
        }
        PredictiveBackHandler(!closing && dismissEnabled) { events ->
            try { events.collect { state.seekTo(it.progress, false) }; close { dismiss() } }
            catch (cancel: CancellationException) { if (!closing) scope.launch { state.animateTo(true) }; throw cancel }
        }
        ProvideIconTextContextMenu {
        CompositionLocalProvider(LocalDialogActions provides DialogActions({ closing }, ::close),
            LocalDialogReveal provides reveal) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(MaterialTheme.colorScheme.scrim)
                    .clickable(enabled = !closing && dismissEnabled, interactionSource = null, indication = null) { close { dismiss() } }
                    .testTag("alert-dialog-scrim").clearAndSetSemantics { })
                Surface(modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(24.dp)
                    .widthIn(min = 280.dp, max = 560.dp).graphicsLayer {
                        alpha = opacity; scaleX = .9f + .1f * reveal; scaleY = .6f + .4f * reveal
                    }.testTag("alert-dialog-container")
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); waitForUpOrCancellation() } },
                    shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation) {
                    // Cancel the window's nonuniform scale before drawing its content. Layout stays
                    // stable, while the Surface clip and background expand around native-size glyphs.
                    Column(Modifier.padding(24.dp).graphicsLayer {
                        alpha = body
                        scaleX = 1f / (.9f + .1f * reveal)
                        scaleY = 1f / (.6f + .4f * reveal)
                    }) {
                        icon?.let { Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)) {
                            CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.iconContentColor, content = it)
                        } }
                        title?.let { Box(Modifier.padding(bottom = 16.dp).semantics { heading() }) {
                            CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.headlineSmall, it)
                            }
                        } }
                        text?.let { Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp)) {
                            CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium, it)
                            }
                        } }
                        FlowRow(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            dismissButton?.invoke(); confirmButton()
                        }
                    }
                }
            }
        }
        }
    }
}

/** Feedback starts immediately; a terminal action runs once after the window has faded out. */
@Composable
fun dialogFeedbackClick(action: () -> Unit): () -> Unit {
    val actions = LocalDialogActions.current
    val tick = feedbackClick { }
    return { if (actions?.isClosing?.invoke() != true) { tick(); if (actions == null) action() else actions.close(action) } }
}

/** Async validation can keep the dialog open on failure and animate only a successful result. */
@Composable
fun rememberDialogClose(): (() -> Unit) -> Unit = LocalDialogActions.current?.close ?: { it() }
