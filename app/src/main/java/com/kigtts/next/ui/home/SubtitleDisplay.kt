@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
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

private data class SubtitleFrame(val text: String, val revision: Int)

@Composable
fun SubtitleDisplay(state: AppState, model: AppViewModel, text: String, style: TextStyle, width: Int, height: Int) {
    val target = SubtitleFrame(text, state.subtitleRevision)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    AnimatedContent(targetState = target, modifier = Modifier.fillMaxWidth().testTag("subtitle-text-transition"),
        contentAlignment = Alignment.Center, label = "subtitle-text",
        transitionSpec = {
            (fadeIn(effects) + slideInVertically(spatial) { it.coerceAtMost(24) / 2 })
                .togetherWith(fadeOut(effects) + slideOutVertically(spatial) { -it.coerceAtMost(24) / 2 }).using(null)
        }) { frame ->
        Box(Modifier.then(if (frame == target) Modifier else Modifier.clearAndSetSemantics {})) {
            val fitted = style.copy(fontSize = fittingSubtitleFontSize(frame.text, style, width, height).sp)
            Text(frame.text, Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("speech-editor")
                .subtitleInputGestures(model, frame == target), style = fitted)
        }
    }
}

/** Programmatic changes animate the retained editor without replacing its IME/selection state. */
@Composable
fun Modifier.subtitleUpdateMotion(revision: Int): Modifier {
    val alpha = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(revision) { alpha.snapTo(.75f); alpha.animateTo(1f, spec) }
    return graphicsLayer { this.alpha = alpha.value }
}
