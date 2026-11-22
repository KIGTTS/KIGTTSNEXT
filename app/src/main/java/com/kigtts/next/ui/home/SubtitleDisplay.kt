package com.kigtts.next.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.text.*
import com.kigtts.next.theme.rememberThemeTint

@Composable
fun SubtitleDisplay(state: AppState, model: AppViewModel, text: String, style: TextStyle, width: Int, height: Int,
    hintOpacity: State<Float> = animateFloatAsState(if (state.editing) .28f else 1f, tween(180))) {
    val placeholder = text.isEmpty()
    val paragraph = remember(text, state.subtitleFormats, state.subtitlePlaceholder) {
        if (placeholder) AnnotatedString(state.subtitlePlaceholder) else formattedText(text, state.subtitleFormats)
    }
    val textColor = style.color
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bodyThemeTint = rememberThemeTint(textColor)
    val hintThemeTint = rememberThemeTint(hintColor)
    val bodyTint = bodyThemeTint ?: remember(textColor) { ColorProducer { textColor } }
    val hintTint = hintThemeTint ?: remember(hintColor) { ColorProducer { hintColor } }
    val themeOwned = placeholder || bodyThemeTint != null || textColor == MaterialTheme.colorScheme.onSurface
    // Keep the theme out of the frame identity while preserving an explicit per-frame color.
    val fitted = style.copy(fontSize = stableSubtitleFontSize(paragraph, style, width, height).sp,
        color = if (themeOwned) Color.Unspecified else textColor)
    val fade = rememberSubtitlePresentation(SubtitleRenderFrame(paragraph, fitted, placeholder, state.subtitleRevision),
        state.subtitleAppearance.reducedMotion)
    val alignment = state.subtitleAppearance.alignment.boxAlignment
    val entryLift = if (state.subtitleAppearance.reducedMotion) 0f else with(LocalDensity.current) { 8.dp.toPx() }
    Box(Modifier.fillMaxSize().testTag("subtitle-text-transition"), contentAlignment = alignment) {
        if (fade.alpha.value < 1f) fade.leaving.forEach { old ->
            val outgoingTint = (if (old.frame.placeholder) hintTint else bodyTint)
                .takeIf { old.frame.style.color == Color.Unspecified }
            Box(Modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = alignment) {
                BasicText(old.frame.paragraph, Modifier.fillMaxWidth().graphicsLayer {
                    alpha = (1f - fade.alpha.value) * old.opacity * if (old.frame.placeholder) hintOpacity.value else 1f
                    translationY = -entryLift * fade.alpha.value
                }, style = old.frame.style, color = outgoingTint)
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = fade.alpha.value * if (placeholder) hintOpacity.value else 1f
            translationY = entryLift * (1f - fade.alpha.value)
        }.then(if (placeholder && paragraph.isNotEmpty()) Modifier.testTag("subtitle-placeholder") else Modifier)) {
            SubtitleDisplayParagraph(paragraph, fitted, width, height, alignment,
                animateReflow = state.subtitleAppearance.displayReflow && !state.subtitleAppearance.reducedMotion,
                model = model, tint = if (placeholder) hintTint else bodyTint)
        }
    }
}
