package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/** Observe first touch without consuming it: native MD3 Slider continues to own position and drag interactions. */
@Composable
internal fun Modifier.cameraSliderPressFeedback(): Modifier {
    val feedback = rememberUpdatedState(LocalHapticFeedback.current)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            feedback.value.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            do { val event = awaitPointerEvent(PointerEventPass.Final) } while (event.changes.any { it.pressed })
        }
    }
}
