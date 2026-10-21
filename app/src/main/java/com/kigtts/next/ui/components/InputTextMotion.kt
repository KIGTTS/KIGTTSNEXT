package com.kigtts.next.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*

/** Only confirmed insertions fade; text, selection and IME composition are never delayed. */
internal fun committedInsertion(before: TextFieldValue, after: TextFieldValue): TextRange? {
    if (after.composition != null || after.text.isEmpty() || after.text.length > 8192) return null
    if (before.text == after.text) return before.composition?.let {
        TextRange(it.min.coerceAtMost(after.text.length), it.max.coerceAtMost(after.text.length)).takeUnless(TextRange::collapsed)
    }
    var start = 0
    while (start < minOf(before.text.length, after.text.length) && before.text[start] == after.text[start]) start++
    var tail = 0
    while (tail < minOf(before.text.length, after.text.length) - start &&
        before.text[before.text.lastIndex - tail] == after.text[after.text.lastIndex - tail]) tail++
    // Keep surrogate pairs intact when two emoji share a leading UTF-16 code unit.
    if (start > 0 && start < after.text.length && after.text[start].isLowSurrogate()) start--
    var end = after.text.length - tail
    if (end < after.text.length && after.text[end].isLowSurrogate()) end++
    return TextRange(start, end).takeIf { start < end }
}

internal fun fadedInsertion(text: AnnotatedString, range: TextRange?, opacity: Float, color: Color): AnnotatedString {
    if (range == null || opacity >= .999f || range.max > text.length) return text
    val from = range.min; val to = range.max
    return AnnotatedString.Builder(text).apply {
        addStyle(SpanStyle(color = color.copy(alpha = color.alpha * opacity)), from, to)
        text.spanStyles.forEach { run ->
            val c = run.item.color
            if (c != Color.Unspecified && run.start < to && run.end > from)
                addStyle(SpanStyle(color = c.copy(alpha = c.alpha * opacity)), maxOf(from, run.start), minOf(to, run.end))
        }
    }.toAnnotatedString()
}

@Composable
internal fun rememberInputTextMotion(value: TextFieldValue, color: Color,
    base: VisualTransformation = VisualTransformation.None): VisualTransformation {
    val opacity = remember { Animatable(1f) }
    var range by remember { mutableStateOf<TextRange?>(null) }
    val history = remember { arrayOf(value) }
    LaunchedEffect(value.text, value.composition) {
        val before = history[0]; history[0] = value
        range = committedInsertion(before, value)
        if (range == null) opacity.snapTo(1f)
        else { opacity.snapTo(.55f); opacity.animateTo(1f, tween(110, easing = LinearOutSlowInEasing)) }
    }
    val alpha = opacity.value
    val visibleRange = range.takeIf { value.composition == null && history[0].text == value.text }
    return remember(base, visibleRange, alpha, color) {
        VisualTransformation { text ->
            val transformed = base.filter(text)
            TransformedText(fadedInsertion(transformed.text, visibleRange, alpha, color), transformed.offsetMapping)
        }
    }
}
