package com.kigtts.next.ui.plugins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.plugin.api.*

@Composable internal fun ChatThought(message: ChatMessage, readingInteraction: () -> Unit = {}, nested: Boolean = false) {
    val details = message.reasoning.isNotBlank() || message.summary.isNotBlank()
    if (!message.thinking && !details && !message.hasReasoning) return
    var expanded by remember(message.id) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(if (nested) 1f else .94f).testTag("chat-thought-step/" + message.id),
        shape = MaterialTheme.shapes.large, color = if (nested) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            ChatStepHeader(if (message.thinking) "思考中" else "已思考", Icons.Default.AutoAwesome,
                expanded, message.thinking, Modifier.testTag("chat-thought/" + message.id), expandable = details) {
                readingInteraction(); expanded = !expanded
            }
            AnimatedVisibility(expanded && details) {
                Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberChatInnerScroll(message.id))
                    .testTag("chat-reasoning-scroll/" + message.id).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (message.summary.isNotBlank()) ChatRichText(message.summary, small = true)
                    if (message.reasoning.isNotBlank()) ChatRichText(message.reasoning, small = true)
                }
            }
        }
    }
}
