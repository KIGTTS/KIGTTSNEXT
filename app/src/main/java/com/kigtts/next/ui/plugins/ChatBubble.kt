package com.kigtts.next.ui.plugins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.vector
import com.kigtts.plugin.api.*

@Composable internal fun ChatBubble(message: ChatMessage) {
    var showReasoning by remember(message.id) { mutableStateOf(false) }
    val user = message.role == ChatRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(Modifier.fillMaxWidth(if (user) .88f else .94f).testTag("chat-message/" + message.id),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = if (user) 4.dp else 20.dp, bottomStart = if (user) 20.dp else 4.dp),
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.reasoning.isNotBlank()) {
                    TextButton(onClick = feedbackClick { showReasoning = !showReasoning }) { Text(if (showReasoning) "收起思考" else "查看思考") }
                    AnimatedVisibility(showReasoning) { ChatRichText(message.reasoning, small = true) }
                }
                if (!showReasoning && message.summary.isNotBlank()) Text(message.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer { ChatRichText(message.text.ifBlank { if (message.state == ChatMessageState.STREAMING) "正在回复…" else "" }) }
                if (message.state == ChatMessageState.CANCELLED || message.state == ChatMessageState.FAILED)
                    Text(if (message.state == ChatMessageState.CANCELLED) "已停止" else "回复未完成", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
@Composable internal fun ChatToolGroup(messages: List<ChatMessage>) {
    var expanded by remember(messages.first().id) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("chat-tools/" + messages.first().id), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(8.dp)) {
            TextButton(onClick = feedbackClick { expanded = !expanded }) {
                Icon(Icons.Default.Build, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("工具调用 · ${messages.size} 项"); Spacer(Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "收起工具调用" else "展开工具调用")
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    messages.forEach { message ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(message.icon.vector(), null, Modifier.size(18.dp))
                            Column { Text(message.toolName.ifBlank { "工具调用" }, style = MaterialTheme.typography.labelLarge)
                                Text(message.text, style = MaterialTheme.typography.bodySmall)
                                if (message.detail.isNotBlank()) ChatRichText(message.detail, small = true) }
                        }
                    }
                }
            }
        }
    }
}
