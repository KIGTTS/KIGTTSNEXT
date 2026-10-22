package com.kigtts.next.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

// Android popups/dialogs replace LocalHapticFeedback with their own window's implementation.
// Keep the host policy in a separate local that survives those window boundaries.
private val LocalButtonFeedback = compositionLocalOf<HapticFeedback?> { null }
internal interface ClickFeedbackAction : () -> Unit
private interface ChangeFeedbackAction : (Boolean) -> Unit

/** One native tick per committed action; dragging, cancelled presses and disabled buttons stay quiet. */
@Composable
fun feedbackClick(action: () -> Unit): () -> Unit {
    if (action is ClickFeedbackAction) return action
    val feedback = LocalButtonFeedback.current ?: LocalHapticFeedback.current
    return object : ClickFeedbackAction {
        override fun invoke() { feedback.performHapticFeedback(HapticFeedbackType.VirtualKey); action() }
    }
}

@Composable
fun feedbackChange(action: (Boolean) -> Unit): (Boolean) -> Unit {
    if (action is ChangeFeedbackAction) return action
    val feedback = LocalButtonFeedback.current ?: LocalHapticFeedback.current
    return object : ChangeFeedbackAction {
        override fun invoke(value: Boolean) {
            feedback.performHapticFeedback(if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            action(value)
        }
    }
}

/** Also gates the native long-press feedback supplied by Compose controls. */
@Composable
fun ProvideButtonFeedback(enabled: Boolean, content: @Composable () -> Unit) {
    val native = LocalHapticFeedback.current
    val feedback = remember(native, enabled) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (enabled) native.performHapticFeedback(hapticFeedbackType)
            }
        }
    }
    CompositionLocalProvider(LocalButtonFeedback provides feedback, LocalHapticFeedback provides feedback, content = content)
}
