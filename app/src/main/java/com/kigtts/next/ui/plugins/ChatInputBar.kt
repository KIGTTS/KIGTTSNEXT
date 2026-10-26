package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.AnimatedButtonContent
import com.kigtts.next.ui.components.IconActionHint
import com.kigtts.next.ui.components.iconLongAction
import com.kigtts.plugin.api.*
import java.io.File

/** Keystrokes invalidate the composer only, rather than the message tree. */
@Composable internal fun ChatInputBar(chat: PageBlock.Chat, enabled: Boolean, resolve: (PluginResource) -> File?,
    invoke: (String, Map<String, String>) -> Unit, owner: String = "", sent: () -> Unit) {
    var draft by rememberSaveable(chat.conversationId, chat.sendAction) { mutableStateOf("") }
    // Other conversations may run independently; their activity only affects the header indicator.
    val running = chat.running
    val canSend = draft.isNotBlank() || (chat.draftImages.isNotEmpty() || chat.draftFiles.isNotEmpty())
    fun send(action: String) { val text = draft; draft = ""; sent(); invoke(action, mapOf("message" to text)) }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().testTag("plugin-chat-composer")) { Column {
        if (chat.queued.isNotEmpty()) ChatQueuePane(chat, enabled, invoke)
        ChatComposerControls(chat, enabled, owner, invoke)
        if (chat.draftImages.isNotEmpty()) Box(Modifier.padding(horizontal = 12.dp)) {
            ChatImages(chat.draftImages, resolve) { index -> chat.removeImageAction?.let { invoke(it, mapOf("value" to chat.draftImages[index].source.path)) } }
        }
        if (chat.draftFiles.isNotEmpty()) Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { ChatFiles(chat.draftFiles, invoke) { file -> chat.removeFileAction?.let { invoke(it, mapOf("value" to file.source.path)) } } }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chat.imageAction?.let { action -> IconButton(onClick = feedbackClick { invoke(action, emptyMap()) },
                modifier = Modifier.testTag("plugin-chat-image").iconLongAction(enabled && chat.draftImages.size < 4) { invoke(action, mapOf("source" to "camera")) },
                enabled = enabled && chat.draftImages.size < 4) { Icon(Icons.Default.AddPhotoAlternate, "添加图片，长按拍照") } }
            chat.fileAction?.let { action -> IconButton(onClick = feedbackClick { invoke(action, emptyMap()) }, enabled = enabled && chat.draftFiles.size < 8, modifier = Modifier.testTag("plugin-chat-file")) { Icon(Icons.Default.AttachFile, "添加文件") } }
            OutlinedTextField(draft, { if (it.length <= 16000) draft = it }, Modifier.weight(1f).testTag("plugin-chat-input"), placeholder = { Text(chat.placeholder) }, maxLines = 5, shape = MaterialTheme.shapes.large, enabled = enabled)
            val action = when { !running -> ChatPrimaryAction.SEND; canSend -> ChatPrimaryAction.QUEUE; else -> ChatPrimaryAction.STOP }
            val label = when (action) { ChatPrimaryAction.SEND -> "发送"; ChatPrimaryAction.STOP -> "停止生成"; ChatPrimaryAction.QUEUE -> "加入队列" }
            IconActionHint(label) {
            IconButton(onClick = feedbackClick {
                if (action == ChatPrimaryAction.STOP) invoke(chat.stopAction, emptyMap())
                else send(if (action == ChatPrimaryAction.QUEUE) chat.queueAction ?: chat.sendAction else chat.sendAction)
            }, enabled = if (action == ChatPrimaryAction.STOP) true else enabled && canSend && (!running || chat.queueAction != null),
                modifier = Modifier.testTag(if (action == ChatPrimaryAction.STOP) "plugin-chat-stop" else "plugin-chat-send").iconLongAction(true, null)) {
                AnimatedButtonContent(action) { state ->
                    Icon(when (state) { ChatPrimaryAction.SEND -> Icons.AutoMirrored.Filled.Send; ChatPrimaryAction.STOP -> Icons.Default.Stop; ChatPrimaryAction.QUEUE -> Icons.AutoMirrored.Filled.PlaylistAdd }, label)
                }
            }
            }
        }
    } }
}
private enum class ChatPrimaryAction { SEND, STOP, QUEUE }
