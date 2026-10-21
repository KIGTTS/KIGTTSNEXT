@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.kigtts.next.state.*
import com.kigtts.next.text.*

private data class SubtitleFrame(val text: String, val revision: Int, val formats: List<TextFormatRun>, val placeholder: String = "")

@Composable
fun SubtitleDisplay(state: AppState, model: AppViewModel, text: String, style: TextStyle, width: Int, height: Int) {
    val target = SubtitleFrame(text, state.subtitleRevision, state.subtitleFormats, state.subtitlePlaceholder.takeIf { text.isEmpty() }.orEmpty())
    val effects = tween<Float>(180, easing = FastOutSlowInEasing)
    AnimatedContent(targetState = target, modifier = Modifier.fillMaxSize().testTag("subtitle-text-transition"),
        contentAlignment = Alignment.Center, label = "subtitle-text",
        transitionSpec = {
            fadeIn(effects)
                .togetherWith(fadeOut(effects))
                .using(null)
        }) { frame ->
        Box(Modifier.fillMaxSize().then(if (frame == target) Modifier else Modifier.clearAndSetSemantics {}), contentAlignment = Alignment.Center) {
            if (frame.text.isEmpty()) {
                // Fit only the displayed hint; the editable document remains empty.
                val fitted = animatedSubtitleStyle(style, fittingSubtitleFontSize(frame.placeholder, style, width, height))
                Box(if (frame.placeholder.isNotEmpty()) Modifier.testTag("subtitle-placeholder") else Modifier) {
                    Text(frame.placeholder, Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("speech-editor")
                        .subtitleInputGestures(model, frame == target),
                        style = fitted,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val formatted = remember(frame.text, frame.formats) { formattedText(frame.text, frame.formats) }
                val fitted = animatedSubtitleStyle(style, fittingSubtitleFontSize(formatted, style, width, height))
                Text(formatted, Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("speech-editor")
                    .subtitleInputGestures(model, frame == target), style = fitted)
            }
        }
    }
}
