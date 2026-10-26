@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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

@Composable internal fun ChatBubble(message: ChatMessage, resolve: (PluginResource) -> java.io.File? = { null }, readingInteraction: () -> Unit = {}, showThought: Boolean = true,
    invoke: ((String, Map<String, String>) -> Unit)? = null) {
    val user = message.role == ChatRole.USER
    val displayText = message.text.ifBlank { if (message.state == ChatMessageState.STREAMING && !message.thinking) "正在回复…" else "" }
    Column(Modifier.fillMaxWidth().testTag("chat-message/" + message.id), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (!user && showThought) ChatThought(message, readingInteraction)
    val status = message.state == ChatMessageState.CANCELLED || message.state == ChatMessageState.FAILED
    if (displayText.isNotBlank() || message.images.isNotEmpty() || message.files.isNotEmpty() || message.options.isNotEmpty() || status) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(Modifier.fillMaxWidth(if (user) .88f else .94f).testTag("chat-answer/" + message.id),
            shape = RoundedCornerShape(topStart = if (user) 20.dp else 4.dp, topEnd = if (user) 4.dp else 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!user) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("回答", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (message.images.isNotEmpty()) ChatImages(message.images, resolve)
                if (message.files.isNotEmpty()) ChatFiles(message.files, invoke)
                if (displayText.isNotBlank()) SelectionContainer { ChatRichText(displayText) }
                if (message.options.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        message.options.forEach { option ->
                            AssistChip(
                                onClick = { invoke?.invoke(option.action, mapOf("value" to option.value, "option" to option.label)) },
                                enabled = invoke != null,
                                label = { Text(option.label) },
                                modifier = Modifier.testTag("chat-option/${message.id}/${option.action}")
                            )
                        }
                    }
                }
                if (message.state == ChatMessageState.CANCELLED || message.state == ChatMessageState.FAILED)
                    Text(if (message.state == ChatMessageState.CANCELLED) "已停止" else "回复未完成", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    }
    }
}
