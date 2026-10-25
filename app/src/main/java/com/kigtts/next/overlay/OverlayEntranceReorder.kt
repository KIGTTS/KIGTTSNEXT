package com.kigtts.next.overlay

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    active: (String?) -> Unit, move: (String, Int) -> Boolean, scrollBack: () -> Unit, center: (Offset?) -> Unit = {}): Modifier {
    val currentId by rememberUpdatedState(id)
    val onCenter by rememberUpdatedState(center)
    val onActive by rememberUpdatedState(active)
    val onMove by rememberUpdatedState(move)
    val onScroll by rememberUpdatedState(scrollBack)
    val feedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val step = with(density) { 48.dp.toPx() }
    val topZone = with(density) { 72.dp.toPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        coroutineScope {
            var dragged: String? = null
            var distance = 0f
            var heldCenter = Offset.Zero
            var touch = Offset.Zero
            var automatic: Job? = null
            fun finish() { automatic?.cancel(); automatic = null; if (dragged != null) { dragged = null; onActive(null); onCenter(null) } }
            try {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        // Keep the original grab point. Centering the icon on the finger
                        // jumps when a hold starts near its edge; moving local coordinates
                        // also feed our own reorder translation back into the next event.
                        heldCenter = origin + Offset(size.width / 2f, size.height / 2f)
                        touch = origin + local
                        onCenter(heldCenter)
                        dragged = currentId; distance = 0f; onActive(dragged)
                        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { finish() }, onDragCancel = { finish() },
                    onDrag = { change, amount ->
                        change.consume()
                        heldCenter += amount
                        touch += amount
                        onCenter(heldCenter)
                        val target = dragged
                        if (target != null) {
                            if (touch.y < topZone) {
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
