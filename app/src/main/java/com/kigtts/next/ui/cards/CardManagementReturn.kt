package com.kigtts.next.ui.cards

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.*
import com.kigtts.next.ui.navigation.*
import kotlinx.coroutines.CancellationException

/** Restore the browser/FAB's existing guard when selection mode leaves its retained drawing layer. */
@Composable internal fun CardManagementReturn(state: CardManagementState, pageActive: Boolean) {
    val host = LocalPageToolbarActions.current
    val handler = rememberUpdatedState<() -> Boolean> { if (pageActive && state.active) { state.finish(); true } else false }
    DisposableEffect(host, state.active) {
        val previous = host?.backGuard
        if (state.active) host?.backGuard = handler
        onDispose { host?.let { if (it.backGuard === handler) it.backGuard = previous } }
    }
    RegisterCameraPullGuard(state.active)
    PredictiveBackHandler(pageActive && state.active) { events ->
        try { events.collect { }; state.finish() } catch (_: CancellationException) { }
    }
}
