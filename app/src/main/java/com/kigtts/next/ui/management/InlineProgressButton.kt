package com.kigtts.next.ui.management

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.ManagementProgressAction

/** Stable touch target: the action changes inside its reserved space rather than shifting the text. */
@Composable
internal fun InlineProgressButton(id: String, state: ManagementProgressAction, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit) {
    val progress by animateFloatAsState(state.progress ?: 0f, tween(180), label = "download-progress")
    Box(Modifier.width(88.dp).height(48.dp).testTag("managed-action/$id"), contentAlignment = Alignment.Center) {
        AnimatedContent(state.running, label = "download-button") { running ->
            if (running) IconButton(onClick = feedbackClick { state.action?.let { invoke(it, emptyMap()) } },
                enabled = enabled && state.action != null,
                modifier = Modifier.size(48.dp).semantics { contentDescription = state.label }) {
                if (state.progress == null) CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 2.5.dp)
                else CircularProgressIndicator(progress = { progress }, Modifier.size(34.dp), strokeWidth = 2.5.dp)
                Icon(Icons.Default.Stop, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            } else FilledTonalButton(onClick = feedbackClick { state.action?.let { invoke(it, emptyMap()) } },
                enabled = enabled && state.action != null, shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(36.dp)) {
                Text(state.label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
