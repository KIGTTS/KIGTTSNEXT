package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.kigtts.next.state.*
import com.kigtts.next.text.*
import com.kigtts.next.ui.components.richTextMenu
import com.kigtts.next.ui.components.textMenuAnchor

@Composable
internal fun SubtitleInput(state: AppState, model: AppViewModel, style: TextStyle, width: Int, height: Int, focus: FocusRequester,
    hintOpacity: State<Float> = animateFloatAsState(if (state.editing) .28f else 1f, tween(180))) {
    val formatted = remember(state.editorText, state.subtitleFormats) { formattedText(state.editorText, state.subtitleFormats) }
    // The departing hint belongs to a different paragraph. Never resize it using the
    // first typed character's metrics while AnimatedVisibility is fading it away.
    val placeholder = remember(state.subtitlePlaceholder) { androidx.compose.ui.text.AnnotatedString(state.subtitlePlaceholder) }
    val placeholderSize = stableSubtitleFontSize(placeholder, style, width, height)
    val placeholderStyle = style.copy(fontSize = placeholderSize.sp)
    val measured = if (formatted.isEmpty()) placeholderSize else stableSubtitleFontSize(formatted, style, width, height)
    val fitted = style.copy(fontSize = measured.sp)
    val base = remember(state.subtitleFormats) { RichTextVisualTransformation(state.subtitleFormats) }
    val alignment = state.subtitleAppearance.alignment.boxAlignment
    val direction = LocalLayoutDirection.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val nativeSelection = state.editorValue.composition != null || !state.editorValue.selection.collapsed
    val reduced = state.subtitleAppearance.reducedMotion
    // A presentation revision and its measured paragraph must arrive together.
    val currentLayout = layout?.takeIf { it.layoutInput.text.text == state.editorText }
    val motion = rememberSubtitleGlyphMotion(currentLayout, width, height, alignment, state.editorValue.selection.min, !nativeSelection,
        SubtitleGlyphPolicy(reducedMotion = reduced, presentationRevision = state.subtitleRevision))
    // While a new paragraph is being laid out, retain the last drawn frame instead
    // of briefly revealing native text at its destination before animating it.
    val measuredCurrentGeometry = currentLayout?.let { it.layoutInput.style.fontSize == fitted.fontSize &&
        it.layoutInput.constraints.maxWidth == width } == true
    val custom = !nativeSelection && motion.frame != null &&
        (!measuredCurrentGeometry || (currentLayout?.size?.height ?: 0) <= height)
    val menu = Modifier.richTextMenu(state.editing && state.subtitleAppearance.richTextEditing,
        { model.state.value.editorValue.selection }, state.subtitleFormats, model::formatSubtitle)
    CompositionLocalProvider(LocalSubtitleMagnifierEnabled provides false) {
        BasicTextField(state.editorValue, model::editText,
            modifier = Modifier.fillMaxSize().testTag("speech-editor").focusRequester(focus).onPreviewKeyEvent { event ->
                if (state.autoPlayback && event.key == Key.Enter && !event.isShiftPressed) {
                    if (event.type == KeyEventType.KeyUp) model.submitSubtitle()
                    true
                } else false
            }.then(menu).textMenuAnchor {
                val current = model.state.value.editorValue
                model.editText(current.copy(selection = androidx.compose.ui.text.TextRange(current.selection.end)))
            },
            textStyle = fitted, cursorBrush = SolidColor(if (custom) Color.Transparent else MaterialTheme.colorScheme.primary), visualTransformation = base,
            interactionSource = interactions,
            onTextLayout = { layout = it },
            keyboardOptions = KeyboardOptions(imeAction = if (state.autoPlayback) ImeAction.Send else ImeAction.Default),
            keyboardActions = KeyboardActions(onSend = { model.submitSubtitle() }),
            decorationBox = { field ->
                // The viewport never animates its measured height. Only glyph layers move;
                // a new line therefore cannot be clipped by the previous line's bounds.
                Box(Modifier.fillMaxSize().testTag("subtitle-input-viewport"), contentAlignment = alignment) {
                    Box(Modifier.fillMaxWidth().testTag("subtitle-input-glyphs").drawWithContent {
                        if (!custom) drawContent()
                    }, propagateMinConstraints = true) { field() }
                    SubtitleGlyphCanvas(motion, custom, Modifier.matchParentSize().clearAndSetSemantics { })
                    AnimatedVisibility(state.editorText.isEmpty() && state.subtitlePlaceholder.isNotEmpty(),
                        enter = fadeIn(tween(170)) + if (reduced) EnterTransition.None else slideInVertically(tween(170)) { (it * .12f).toInt() },
                        exit = fadeOut(tween(150)) + if (reduced) ExitTransition.None else slideOutVertically(tween(150)) { -(it * .12f).toInt() }) {
                        Text(state.subtitlePlaceholder, Modifier.fillMaxWidth().graphicsLayer { alpha = hintOpacity.value }.testTag("subtitle-placeholder"),
                            style = placeholderStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (custom && focused && windowFocused && state.editorValue.selection.collapsed) {
                        // Read the measured paragraph directly, without waiting for
                        // the glyph animation's SideEffect to commit another frame.
                        val cursorFrame = layout?.let {
                            val viewport = IntSize(width, height)
                            val origin = alignment.align(it.size, viewport, direction)
                            SubtitleGlyphFrame(it, Offset(origin.x.toFloat(), origin.y.toFloat()), emptyList(), viewport)
                        }
                        SubtitleCursorMotion(cursorFrame, state.editorValue.selection.end, MaterialTheme.colorScheme.primary,
                            Modifier.matchParentSize().clearAndSetSemantics { }.testTag("subtitle-caret"), animateMovement = !reduced)
                    }
                }
            })
    }
}
