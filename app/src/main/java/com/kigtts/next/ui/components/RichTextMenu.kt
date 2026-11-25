package com.kigtts.next.ui.components

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import com.kigtts.next.text.*

/** Formatting lives in the selection menu. Modal pickers retain the original selection. */
@Composable
internal fun Modifier.richTextMenu(enabled: Boolean, selection: () -> TextRange, formats: List<TextFormatRun>,
    format: (TextRange, TextFormat, Long) -> Unit): Modifier {
    var colorRange by remember { mutableStateOf<TextRange?>(null) }
    var sizeRange by remember { mutableStateOf<TextRange?>(null) }
    val selected = rememberUpdatedState(selection)
    val apply = rememberUpdatedState(format)
    val interactive = rememberUpdatedState(enabled)
    val bold = uiText("subtitle.format_bold")
    val italic = uiText("subtitle.format_italic")
    val underline = uiText("subtitle.format_underline")
    val color = uiText("photo.text_color")
    val size = uiText("fonts.size")
    LaunchedEffect(enabled) { if (!enabled) { colorRange = null; sizeRange = null } }
    colorRange?.let { range ->
        val initial = formats.lastOrNull { it.kind == TextFormat.COLOR && it.start <= range.min && it.end >= range.max }?.value ?: 0xFF00696E
        ColorPickerDialog(initial, { if (enabled) apply.value(range, TextFormat.COLOR, it); colorRange = null }, { colorRange = null }, title = uiText("photo.text_color"))
    }
    sizeRange?.let { range ->
        MotionAlertDialog(onDismissRequest = { sizeRange = null }, title = { Text(uiText("fonts.size")) }, text = {
            val small = uiText("subtitle.format_small")
            val normal = uiText("subtitle.format_normal")
            val large = uiText("subtitle.format_large")
            ChoiceButtonGroup(size, listOf(75L,100L,125L),
                formats.lastOrNull { it.kind == TextFormat.SIZE && it.start <= range.min && it.end >= range.max }?.value ?: 100L,
                { when(it) { 75L -> small; 125L -> large; else -> normal } },
                { if (enabled) apply.value(range, TextFormat.SIZE, it); sizeRange = null }, enabled = enabled)
        }, confirmButton = { MotionTextButton(dialogFeedbackClick { sizeRange = null }) { Text(uiText("action.cancel")) } })
    }
    return appendTextContextMenuComponents {
        if (interactive.value && !selected.value().collapsed) {
            separator()
            listOf(TextFormat.BOLD to bold, TextFormat.ITALIC to italic, TextFormat.UNDERLINE to underline).forEach { (kind, label) ->
                // Keep the selection toolbar for consecutive formatting actions.
                // Closing here races Foundation's showFloatingToolbar after text relayout.
                item(kind, label) { if (interactive.value) apply.value(selected.value(), kind, 0) }
            }
            item(TextFormat.COLOR, color) { colorRange = selected.value(); close() }
            item(TextFormat.SIZE, size) { sizeRange = selected.value(); close() }
        }
    }
}
