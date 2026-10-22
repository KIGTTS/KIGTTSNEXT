package com.kigtts.next.ui.home

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue

internal data class SubtitleInputFrame(val text: AnnotatedString, val style: TextStyle)
private class LastSubtitleFrame(var value: SubtitleInputFrame)

/** Fade cleared glyphs inside a stable viewport, keeping the active editor fully opaque. */
@Composable
internal fun rememberSubtitleEditMotion(frame: SubtitleInputFrame, value: TextFieldValue): SubtitleEditMotion {
    val last = remember { LastSubtitleFrame(frame) }
    val departing = remember(value.text, value.composition) { last.value }
    val previous = remember { arrayOf(value) }
    var ghost by remember { mutableStateOf<SubtitleInputFrame?>(null) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(value.text, value.composition, value.selection) {
        val before = previous[0]
        previous[0] = value
        last.value = frame
        ghost = null
        // Keep native composing text and range selection immediate. Never create another
        // editable field: only the previous, committed glyphs can briefly depart.
        if (value.composition != null || !value.selection.collapsed ||
            (before.text == value.text && before.composition == null)) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        ghost = departing.takeIf { value.text.isEmpty() && before.composition == null && it.text.isNotEmpty() }
        if (ghost == null) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        ghost = null
    }
    // Style changes alone must update the next departing frame, too.
    SideEffect { last.value = frame }
    return SubtitleEditMotion(ghost, progress.value)
}

internal data class SubtitleEditMotion(val ghost: SubtitleInputFrame?, val progress: Float)
