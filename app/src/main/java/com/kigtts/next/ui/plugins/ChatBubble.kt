@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.vector
import com.kigtts.plugin.api.*

@Composable internal fun ChatBubble(message: ChatMessage, resolve: (PluginResource) -> java.io.File? = { null }) {
    val user = message.role == ChatRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(Modifier.fillMaxWidth(if (user) .88f else .94f).testTag("chat-message/" + message.id),
            shape = RoundedCornerShape(topStart = if (user) 20.dp else 4.dp, topEnd = if (user) 4.dp else 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val thinking = message.thinking
                ChatThought(message)
                if (message.images.isNotEmpty()) ChatImages(message.images, resolve)
                SelectionContainer { ChatRichText(message.text.ifBlank { if (message.state == ChatMessageState.STREAMING && !thinking) "正在回复…" else "" }) }
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
                    messages.forEach { message -> key(message.id) { ChatToolItem(message) } }
                }
            }
        }
    }
}

@Composable private fun ChatToolItem(message: ChatMessage) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    Column {
        TextButton(onClick = feedbackClick { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("chat-tool/" + message.id)) {
            Icon(message.icon.vector(), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(message.toolName.ifBlank { "工具调用" }, style = MaterialTheme.typography.labelLarge)
                Text(if (message.state == ChatMessageState.STREAMING) "正在执行" else message.summary.ifBlank { "执行结束" }, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            if (message.state == ChatMessageState.STREAMING) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        AnimatedVisibility(expanded) { Column(Modifier.padding(start = 12.dp)) {
            if (message.detail.isNotBlank()) ToolSection("执行过程", message.detail)
            if (message.text.isNotBlank()) ToolSection("执行结果", message.text)
        } }
    }
}
@Composable private fun ToolSection(title: String, text: String) {
    var expanded by remember(title) { mutableStateOf(false) }
    Column {
        TextButton(onClick = feedbackClick { expanded = !expanded }) {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(16.dp))
            Text(title, style = MaterialTheme.typography.labelMedium)
        }
        AnimatedVisibility(expanded) { ChatMarkdownCode(text, "json", true) }
    }
}
