package com.kigtts.next.overlay

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlin.math.abs

/** Normal swipes belong to the dial; holding first starts persistent entrance sorting. */
@Composable
internal fun Modifier.overlayEntranceReorder(id: String, enabled: Boolean,
    active: (String?) -> Unit, move: (String, Int) -> Boolean, scrollBack: () -> Unit): Modifier {
    val currentId by rememberUpdatedState(id)
    val onActive by rememberUpdatedState(active)
    val onMove by rememberUpdatedState(move)
    val onScroll by rememberUpdatedState(scrollBack)
    val feedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val step = with(density) { 48.dp.toPx() }
    val topZone = with(density) { 72.dp.toPx() }
    var top by remember { mutableFloatStateOf(0f) }
    return onGloballyPositioned { top = it.positionInRoot().y }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        coroutineScope {
            var dragged: String? = null
            var distance = 0f
            var automatic: Job? = null
            fun finish() { automatic?.cancel(); automatic = null; dragged = null; onActive(null) }
            try {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragged = currentId; distance = 0f; onActive(dragged)
                        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { finish() }, onDragCancel = { finish() },
                    onDrag = { change, amount ->
                        change.consume()
                        val target = dragged
                        if (target != null) {
                            if (top + change.position.y < topZone) {
                                if (automatic == null) automatic = launch {
                                    while (isActive) {
                                        if (!onMove(target, -1)) break
                                        onScroll()
                                        feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                        delay(260)
                                    }
                                }
                            } else {
                                automatic?.cancel(); automatic = null
                                distance += amount.y
                                if (abs(distance) >= step) {
                                    if (onMove(target, if (distance > 0f) 1 else -1))
                                        feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    distance %= step
                                }
                            }
                        }
                    })
            } finally { finish() }
        }
    }
}
