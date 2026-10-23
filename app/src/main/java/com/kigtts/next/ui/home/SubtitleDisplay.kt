package com.kigtts.next.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import com.kigtts.next.state.*
import com.kigtts.next.text.*

@Composable
fun SubtitleDisplay(state: AppState, model: AppViewModel, text: String, style: TextStyle, width: Int, height: Int,
    hintOpacity: State<Float> = animateFloatAsState(if (state.editing) .28f else 1f, tween(180))) {
    val placeholder = text.isEmpty()
    val paragraph = remember(text, state.subtitleFormats, state.subtitlePlaceholder) {
        if (placeholder) AnnotatedString(state.subtitlePlaceholder) else formattedText(text, state.subtitleFormats)
    }
    val fitted = style.copy(fontSize = stableSubtitleFontSize(paragraph, style, width, height).sp,
        color = if (placeholder) MaterialTheme.colorScheme.onSurfaceVariant else style.color)
    val fade = rememberSubtitlePresentation(SubtitleRenderFrame(paragraph, fitted, placeholder, state.subtitleRevision),
        state.subtitleAppearance.reducedMotion)
    val alignment = state.subtitleAppearance.alignment.boxAlignment
    Box(Modifier.fillMaxSize().testTag("subtitle-text-transition"), contentAlignment = alignment) {
        if (fade.alpha.value < 1f) fade.leaving.forEach { old ->
            Box(Modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = alignment) {
                Text(old.frame.paragraph, Modifier.fillMaxWidth().graphicsLayer {
                    alpha = (1f - fade.alpha.value) * old.opacity * if (old.frame.placeholder) hintOpacity.value else 1f
                }, style = old.frame.style)
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = fade.alpha.value * if (placeholder) hintOpacity.value else 1f
        }.then(if (placeholder && paragraph.isNotEmpty()) Modifier.testTag("subtitle-placeholder") else Modifier)) {
            SubtitleDisplayParagraph(paragraph, fitted, width, height, alignment,
                animateReflow = state.subtitleAppearance.displayReflow && !state.subtitleAppearance.reducedMotion,
                model = model)
        }
    }
}
