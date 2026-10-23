package com.kigtts.next.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private data class SubtitleFrame(val text: String, val formats: List<TextFormatRun>, val placeholder: String = "") {
    fun paragraph() = if (text.isEmpty()) AnnotatedString(placeholder) else formattedText(text, formats)
}
private data class SubtitleRenderFrame(val content: SubtitleFrame, val style: TextStyle)

private class DisplayFade(initial: SubtitleRenderFrame) {
    var current by mutableStateOf(initial)
    var previous by mutableStateOf<SubtitleRenderFrame?>(null)
    var fraction by mutableFloatStateOf(1f)
    var previousOpacity = 1f
    fun update(next: SubtitleRenderFrame) {
        if (next.content != current.content) {
            previous = current
            previousOpacity = fraction
            fraction = 0f
        }
        current = next
    }
}

@Composable
fun SubtitleDisplay(state: AppState, model: AppViewModel, text: String, style: TextStyle, width: Int, height: Int,
    hintOpacity: State<Float> = animateFloatAsState(if (state.editing) .28f else 1f, tween(180))) {
    val target = SubtitleFrame(text, state.subtitleFormats, state.subtitlePlaceholder.takeIf { text.isEmpty() }.orEmpty())
    val paragraph = remember(target) { target.paragraph() }
    val fitted = style.copy(fontSize = stableSubtitleFontSize(paragraph, style, width, height).sp)
    val fade = remember { DisplayFade(SubtitleRenderFrame(target, fitted)) }
    SideEffect { fade.update(SubtitleRenderFrame(target, fitted)) }
    LaunchedEffect(fade.current.content) {
        if (fade.previous != null) {
            animate(0f, 1f, animationSpec = tween(180, easing = FastOutSlowInEasing)) { value, _ -> fade.fraction = value }
            fade.previous = null
        }
    }
    // Live text avoids retained/lookahead animation layouts. Font and viewport
    // measurements update together; only text opacity and translation animate.
    Box(Modifier.fillMaxSize().testTag("subtitle-text-transition"), contentAlignment = state.subtitleAppearance.alignment.boxAlignment) {
        fade.previous?.let { old ->
            Box(Modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = state.subtitleAppearance.alignment.boxAlignment) {
                Text(remember(old.content) { old.content.paragraph() }, Modifier.fillMaxWidth().graphicsLayer {
                    alpha = (1f - fade.fraction) * fade.previousOpacity * if (old.content.text.isEmpty()) hintOpacity.value else 1f
                    translationY = -size.height * .05f * fade.fraction
                }, style = old.style, color = if (old.content.text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else style.color)
            }
        }
        // Render the live fit in this composition; the fade state only remembers
        // the outgoing paragraph, so resizing never waits for a second commit.
        val shown = SubtitleRenderFrame(target, fitted)
        Box(if (shown.content.text.isEmpty() && shown.content.placeholder.isNotEmpty()) Modifier.testTag("subtitle-placeholder") else Modifier) {
            Text(remember(shown.content) { shown.content.paragraph() }, Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("speech-editor")
                .subtitleInputGestures(model).graphicsLayer {
                    alpha = fade.fraction * if (shown.content.text.isEmpty()) hintOpacity.value else 1f
                    translationY = size.height * .05f * (1f - fade.fraction)
                }, style = shown.style, color = if (shown.content.text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else style.color)
        }
    }
}
