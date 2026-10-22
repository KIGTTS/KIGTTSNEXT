package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kigtts.next.state.*
import com.kigtts.next.text.*
import com.kigtts.next.ui.components.richTextMenu
import com.kigtts.next.ui.components.textMenuAnchor

@Composable
internal fun SubtitleInput(state: AppState, model: AppViewModel, style: TextStyle, width: Int, height: Int, focus: FocusRequester) {
    val formatted = remember(state.editorText, state.subtitleFormats) { formattedText(state.editorText, state.subtitleFormats) }
    val measured = stableSubtitleFontSize(if (formatted.isEmpty()) androidx.compose.ui.text.AnnotatedString(state.subtitlePlaceholder) else formatted,
        style, width, height)
    val fitted = style.copy(fontSize = measured.sp)
    val base = remember(state.subtitleFormats) { RichTextVisualTransformation(state.subtitleFormats) }
    val motion = rememberSubtitleEditMotion(SubtitleInputFrame(formatted, fitted), state.editorValue)
    val progress = motion.progress
    val alignment = state.subtitleAppearance.alignment.boxAlignment
    val menu = Modifier.richTextMenu(state.editing && state.subtitleAppearance.richTextEditing,
        { model.state.value.editorValue.selection }, state.subtitleFormats, model::formatSubtitle)
    CompositionLocalProvider(LocalSubtitleMagnifierEnabled provides false) {
        BasicTextField(state.editorValue, model::editText,
            modifier = Modifier.fillMaxSize().testTag("speech-editor").focusRequester(focus).then(menu).textMenuAnchor {
                val current = model.state.value.editorValue
                model.editText(current.copy(selection = androidx.compose.ui.text.TextRange(current.selection.end)))
            },
            textStyle = fitted, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), visualTransformation = base,
            decorationBox = { field ->
                // The viewport never animates its measured height. Only glyph layers move;
                // a new line therefore cannot be clipped by the previous line's bounds.
                Box(Modifier.fillMaxSize().testTag("subtitle-input-viewport"), contentAlignment = alignment) {
                    Box(Modifier.fillMaxWidth().testTag("subtitle-input-glyphs")) { field() }
                    AnimatedVisibility(state.editorText.isEmpty() && state.subtitlePlaceholder.isNotEmpty(),
                        enter = fadeIn(tween(140)), exit = fadeOut(tween(80))) {
                        Text(state.subtitlePlaceholder, Modifier.fillMaxWidth().testTag("subtitle-placeholder"),
                            style = fitted, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    motion.ghost?.let { frame ->
                        Box(Modifier.matchParentSize().clearAndSetSemantics { }.testTag("subtitle-clear-ghost"), contentAlignment = alignment) {
                            Text(frame.text, Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).graphicsLayer {
                                alpha = 1f - progress
                            }, style = frame.style)
                        }
                    }
                }
            })
    }
}
