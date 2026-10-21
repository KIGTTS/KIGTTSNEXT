@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.*

@Composable internal fun ChatThought(message: ChatMessage) {
    val thinking = message.thinking
    val details = message.reasoning.isNotBlank() || message.summary.isNotBlank()
    if (!thinking && !details && !message.hasReasoning) return
    var expanded by remember(message.id) { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chat-thought/" + message.id)
            .clickable(enabled = details, onClick = feedbackClick { expanded = !expanded }),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (thinking) ContainedLoadingIndicator(Modifier.size(28.dp))
            else Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (thinking) "思考中" else "已思考", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (details) Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (expanded) "收起思考" else "展开思考", Modifier.size(20.dp))
        }
        AnimatedVisibility(expanded && details) {
            Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberChatInnerScroll(message.id))
                .testTag("chat-reasoning-scroll/" + message.id), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.summary.isNotBlank()) ChatRichText(message.summary, small = true)
                if (message.reasoning.isNotBlank()) ChatRichText(message.reasoning, small = true)
            }
        }
    }
}
