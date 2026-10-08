package com.kigtts.next.ui.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.*

/** Drawer animations outlive the predictive callback that requested them. */
@Stable
class DrawerNavigation(val state: DrawerState, private val scope: CoroutineScope) {
    private var motion: Job? = null
    private var generation = 0
    private var settling by mutableStateOf(false)
    private var onScreen by mutableStateOf(false)
    val active: Boolean get() = onScreen || settling || state.isAnimationRunning || state.isOpen || state.targetValue == DrawerValue.Open
    fun placed(leftEdge: Float, rightEdge: Float, viewportWidth: Float) {
        onScreen = rightEdge > 1f && leftEdge < viewportWidth - 1f
    }
    fun open() = moveTo(DrawerValue.Open)
    fun close() = moveTo(DrawerValue.Closed)
    private fun moveTo(value: DrawerValue) {
        motion?.cancel()
        val request = ++generation
        settling = true
        motion = scope.launch {
            try {
                if (value == DrawerValue.Open) state.open() else state.close()
            } finally {
                if (request == generation) settling = false
            }
        }
    }
}

@Composable
fun rememberDrawerNavigation(): DrawerNavigation {
    val state = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    return remember(state, scope) { DrawerNavigation(state, scope) }
}
