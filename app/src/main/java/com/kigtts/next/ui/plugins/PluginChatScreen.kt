package com.kigtts.next.ui.plugins

import androidx.compose.animation.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.plugin.api.*
import kotlinx.coroutines.launch

/** User scrolling owns the viewport until they explicitly return to the bottom. */
@Composable
internal fun PluginChatScreen(chat: PageBlock.Chat, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit, header: @Composable ColumnScope.() -> Unit) {
    val list = remember(chat.conversationId) { LazyListState() }
    var draft by rememberSaveable(chat.conversationId, chat.sendAction) { mutableStateOf("") }
    var follow by remember(chat.conversationId) { mutableStateOf(true) }
    val dragging by list.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    val rows = remember(chat.messages) { chatRows(chat.messages) }
    val atBottom by remember(list) { derivedStateOf {
        val info = list.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        info.totalItemsCount == 0 || last != null && last.index == info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset + 8
    } }
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(chat.messages.lastOrNull(), follow, dragging) {
        if (follow && !dragging && rows.isNotEmpty()) list.scrollToItem(rows.size)
    }
    Column(Modifier.fillMaxSize().testTag("plugin-chat")) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().testTag("plugin-chat-history"), list, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item("header") { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = header) }
                items(rows, key = { it.first().id }) { messages ->
                    if (messages.first().role == ChatRole.TOOL) ChatToolGroup(messages) else ChatBubble(messages.first())
                }
            }
            androidx.compose.animation.AnimatedVisibility(!follow && !atBottom, Modifier.align(Alignment.BottomEnd).padding(16.dp), enter = fadeIn(), exit = fadeOut()) {
                SmallFloatingActionButton(onClick = feedbackClick {
                    follow = true; scope.launch { list.animateScrollToItem(rows.size) }
                }, modifier = Modifier.testTag("plugin-chat-bottom")) { Icon(Icons.Default.ArrowDownward, "回到底部") }
            }
        }
        ChatComposerControls(chat, enabled, invoke)
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom,
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
internal fun chatRows(messages: List<ChatMessage>): List<List<ChatMessage>> {
    val rows = mutableListOf<MutableList<ChatMessage>>()
    messages.forEach { message ->
        if (message.role == ChatRole.TOOL && rows.lastOrNull()?.lastOrNull()?.role == ChatRole.TOOL) rows.last().add(message)
        else rows.add(mutableListOf(message))
    }
    return rows
}
