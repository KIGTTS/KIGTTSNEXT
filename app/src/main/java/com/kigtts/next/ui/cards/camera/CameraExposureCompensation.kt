package com.kigtts.next.ui.cards.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/** Both controls drive the same AE target. A focus timeout only discards the local offset. */
internal class CameraExposureCompensation {
    var range by mutableStateOf(0..0); private set
    var step by mutableStateOf(0f); private set
    var baseline by mutableIntStateOf(0); private set
    var focusOffset by mutableIntStateOf(0); private set
    private var preferredEv = 0f
    val focusRange get() = (range.first - baseline)..(range.last - baseline)
    val index get() = baseline + focusOffset

    fun attach(indices: IntRange, evPerStep: Float) {
        range = indices.takeIf { 0 in it && evPerStep.isFinite() && evPerStep > 0f } ?: 0..0
        step = if (range.first < range.last) evPerStep else 0f
        baseline = if (step > 0f) (preferredEv / step).roundToInt().coerceIn(range) else 0
        focusOffset = 0
    }
    fun chooseBaseline(value: Int) {
        baseline = value.coerceIn(range)
        focusOffset = focusOffset.coerceIn(focusRange)
        preferredEv = baseline * step
    }
    fun chooseFocusOffset(value: Int) { focusOffset = value.coerceIn(focusRange) }
    fun clearFocus() { focusOffset = 0 }
    fun detach(keepBaseline: Boolean) {
        if (!keepBaseline) preferredEv = 0f
        attach(0..0, 0f)
    }
}
