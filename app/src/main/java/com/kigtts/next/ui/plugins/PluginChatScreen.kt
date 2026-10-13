package com.kigtts.next.ui.plugins

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.*

/** Generic native chat surface: providers, transport and conversation state all belong to the plugin. */
@Composable
internal fun PluginChatScreen(chat: PageBlock.Chat, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit, header: @Composable ColumnScope.() -> Unit) {
    val list = rememberLazyListState()
    var draft by remember(chat.sendAction) { mutableStateOf("") }
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress }.collect { moving ->
        if (moving) follow = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index == list.layoutInfo.totalItemsCount - 1
    } }
    LaunchedEffect(chat.messages.lastOrNull()?.id, chat.messages.lastOrNull()?.text, chat.messages.lastOrNull()?.reasoning) {
        if (follow && chat.messages.isNotEmpty()) list.scrollToItem(chat.messages.size)
    }
    Column(Modifier.fillMaxSize().testTag("plugin-chat")) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), list, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("header") { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = header) }
            items(chat.messages, key = { it.id }) { message -> ChatBubble(message) }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft, { if (it.length <= 16_000) draft = it }, Modifier.weight(1f).testTag("plugin-chat-input"),
                placeholder = { Text(chat.placeholder) }, maxLines = 5, shape = MaterialTheme.shapes.large, enabled = enabled)
            IconButton(onClick = feedbackClick {
                if (chat.running) invoke(chat.stopAction, emptyMap())
                else if (draft.isNotBlank()) { val message = draft; draft = ""; follow = true; invoke(chat.sendAction, mapOf("message" to message)) }
            }, enabled = enabled && (chat.running || draft.isNotBlank()), modifier = Modifier.testTag("plugin-chat-send")) {
                Icon(if (chat.running) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send, if (chat.running) "停止生成" else "发送")
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    var showReasoning by remember(message.id) { mutableStateOf(false) }
    val user = message.role == ChatRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(Modifier.fillMaxWidth(if (user) .88f else .94f), shape = MaterialTheme.shapes.large,
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.reasoning.isNotBlank()) {
                    TextButton(onClick = feedbackClick { showReasoning = !showReasoning }) { Text(if (showReasoning) "收起思考" else "查看思考") }
                    if (showReasoning) Text(message.reasoning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(message.text.ifBlank { if (message.state == ChatMessageState.STREAMING) "正在回复…" else "" },
                    style = if (message.role == ChatRole.TOOL) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge)
                if (message.state == ChatMessageState.CANCELLED || message.state == ChatMessageState.FAILED)
                    Text(if (message.state == ChatMessageState.CANCELLED) "已停止" else "回复未完成", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
