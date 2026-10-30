package com.kigtts.next.ui.management

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.ManagementProgressAction

/** Stable touch target: the action changes inside its reserved space rather than shifting the text. */
@Composable
internal fun InlineProgressButton(id: String, state: ManagementProgressAction, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit) {
    val progress by animateFloatAsState(state.progress ?: 0f, tween(180), label = "download-progress")
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    // Keep the idle label during download: the cancel label must not change the morph's width.
    var idleLabel by remember { mutableStateOf(state.label) }
    SideEffect { if (!state.running) idleLabel = state.label }
    val idleWidth = with(LocalDensity.current) { measurer.measure(idleLabel, style).size.width.toDp() }
        .plus(24.dp).coerceIn(64.dp, 88.dp)
    val motion = updateTransition(state.running, label = "download-button")
    val width by motion.animateDp({ tween(220, easing = FastOutSlowInEasing) }, label = "width") {
        if (it) 36.dp else idleWidth
    }
    val plate by motion.animateFloat({ tween(180) }, label = "plate") { if (it) 0f else 1f }
    val label by motion.animateFloat({ tween(100, delayMillis = if (targetState) 0 else 80) }, label = "label") {
        if (it) 0f else 1f
    }
    val ring by motion.animateFloat({ tween(120, delayMillis = if (targetState) 80 else 0) }, label = "ring") {
        if (it) 1f else 0f
    }
    val active = enabled && state.action != null
    val colors = MaterialTheme.colorScheme
    Box(Modifier.width(88.dp).height(48.dp).testTag("managed-action/$id")
        .clip(CircleShape).clickable(enabled = active, role = Role.Button,
            onClick = feedbackClick { state.action?.let { invoke(it, emptyMap()) } })
        .semantics { contentDescription = state.label }, contentAlignment = Alignment.Center) {
        Box(Modifier.width(width).height(36.dp).testTag("managed-action-shape/$id").clip(CircleShape)
            .background(colors.secondaryContainer.copy(alpha = plate * if (active) 1f else .38f)),
            contentAlignment = Alignment.Center) {
            Text(idleLabel, Modifier.requiredWidth(idleWidth).graphicsLayer { alpha = label }.clearAndSetSemantics {},
                maxLines = 1, style = style, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = colors.onSecondaryContainer)
        }
        if (motion.currentState || motion.targetState) {
            Box(Modifier.size(36.dp).testTag("managed-action-ring/$id").graphicsLayer { alpha = ring },
                contentAlignment = Alignment.Center) {
                if (state.progress == null) CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 2.5.dp)
                else CircularProgressIndicator(progress = { progress }, Modifier.fillMaxSize(), strokeWidth = 2.5.dp)
                Icon(Icons.Default.Stop, null, Modifier.size(16.dp), tint = colors.primary)
            }
        }
    }
}
