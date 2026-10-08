package com.kigtts.next.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.floor

/** One pickup/release pulse and discrete ticks, rather than vibrating on every pointer sample. */
class DragFeedback(private val native: HapticFeedback) {
    private var bucket = 0
    private var active = false
    fun start(value: Float) {
        bucket = floor(value / 32f).toInt()
        if (!active) {
            active = true
            native.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }
    fun step() {
        if (active) native.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    fun move(value: Float) {
        val next = floor(value / 32f).toInt()
        if (active && next != bucket) {
            bucket = next
            native.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    fun finish(cancelled: Boolean = false) {
        if (active && !cancelled) native.performHapticFeedback(HapticFeedbackType.GestureEnd)
        active = false
    }
}

@Composable
fun rememberDragFeedback(): DragFeedback {
    val native = LocalHapticFeedback.current
    return remember(native) { DragFeedback(native) }
}
