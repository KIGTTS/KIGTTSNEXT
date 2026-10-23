package com.kigtts.next.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import kotlinx.coroutines.delay

internal class PanelIdleChrome {
    var pulse by mutableIntStateOf(0); private set
    var pressed by mutableStateOf(false)
    var idle by mutableStateOf(false)
    fun wake() { pulse++; idle = false }
}

/** Fade only the drawing layer: controls stay reachable and safe areas never collapse. */
@Composable internal fun rememberPanelIdleChrome(enabled: Boolean, keepVisible: Boolean = false): Pair<PanelIdleChrome, State<Float>> {
    val state = remember { PanelIdleChrome() }
    val timeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(6000L,
        containsIcons = true, containsControls = true) ?: 6000L
    LaunchedEffect(enabled, keepVisible, state.pulse, state.pressed, timeout) {
        state.idle = false
        if (enabled && !keepVisible && !state.pressed) { delay(timeout); state.idle = true }
    }
    val alpha = animateFloatAsState(if (state.idle) .18f else 1f, tween(300), label = "panel-idle-controls")
    return state to alpha
}

internal fun Modifier.panelIdleWake(state: PanelIdleChrome): Modifier = onPreviewKeyEvent { state.wake(); false }
    .pointerInput(state) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    state.pressed = event.changes.any { it.pressed }
                    state.wake()
                }
            }
        } finally { state.pressed = false }
    }
