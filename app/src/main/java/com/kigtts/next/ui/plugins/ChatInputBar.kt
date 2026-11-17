package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.AnimatedButtonContent
import com.kigtts.next.ui.components.IconActionHint
import com.kigtts.next.ui.components.iconLongAction
import com.kigtts.next.i18n.uiText
import com.kigtts.next.theme.LocalSecondaryBackground
import com.kigtts.next.ui.home.subtitleCardColor
import com.kigtts.plugin.api.*
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Keystrokes invalidate the composer only, rather than the message tree. */
@Composable internal fun ChatInputBar(chat: PageBlock.Chat, enabled: Boolean, resolve: (PluginResource) -> File?,
    invoke: (String, Map<String, String>) -> Unit, owner: String = "",
    startVoiceInput: (() -> (suspend (Boolean) -> String?)?)? = null, sent: () -> Unit) {
    var draft by rememberSaveable(chat.conversationId, chat.sendAction) { mutableStateOf("") }
    // Other conversations may run independently; their activity only affects the header indicator.
    val running = chat.running
    val canSend = draft.isNotBlank() || (chat.draftImages.isNotEmpty() || chat.draftFiles.isNotEmpty())
    val sendAction = if (running) chat.queueAction else chat.sendAction
    var attachments by remember { mutableStateOf(false) }
    var dictating by remember(chat.conversationId) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(ime, density, focusManager) {
        var wasVisible = false
        snapshotFlow { ime.getBottom(density) > 0 }.collect { visible ->
            if (!visible && wasVisible) focusManager.clearFocus(force = true)
            wasVisible = visible
        }
    }
    val begin by rememberUpdatedState<() -> (suspend (Boolean) -> String?)?> {
        if (dictating) null else startVoiceInput?.invoke()?.also { dictating = true }
    }
    val dispatchVoice by rememberUpdatedState<(String) -> Unit> { text ->
        val action = if (chat.running) chat.queueAction else chat.voiceInputAction ?: chat.sendAction
        if (action != null) { sent(); invoke(action, mapOf("message" to text)) }
    }
    val voiceEnabled = startVoiceInput != null && enabled && sendAction != null
    val askForFocus by rememberUpdatedState { focus.requestFocus(); keyboardController?.show(); Unit }
    fun send(action: String) { val text = draft; draft = ""; sent(); invoke(action, mapOf("message" to text)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (chat.queued.isNotEmpty()) ChatQueuePane(chat, enabled, invoke)
        ChatApprovalPanel(chat, enabled, invoke)
        Surface(color = subtitleCardColor(LocalSecondaryBackground.current != null), shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().testTag("plugin-chat-composer")) { Column {
        if (chat.draftImages.isNotEmpty()) Box(Modifier.padding(horizontal = 12.dp)) {
            ChatImages(chat.draftImages, resolve) { index -> chat.removeImageAction?.let { invoke(it, mapOf("value" to chat.draftImages[index].source.path)) } }
        }
        if (chat.draftFiles.isNotEmpty()) Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { ChatFiles(chat.draftFiles, invoke) { file -> chat.removeFileAction?.let { invoke(it, mapOf("value" to file.source.path)) } } }
        Box {
        TextField(draft, { if (it.length <= 16000) draft = it }, Modifier.fillMaxWidth().testTag("plugin-chat-input").focusRequester(focus).onPreviewKeyEvent { event ->
            if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && !event.isShiftPressed && enabled && sendAction != null && canSend) {
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) send(sendAction)
                true
            } else false
        }, placeholder = { Text(uiText("chat.type_or_talk").takeIf { voiceEnabled } ?: chat.placeholder) }, maxLines = 5, enabled = enabled,
            colors = TextFieldDefaults.colors(focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (enabled && canSend) sendAction?.let(::send) }))
        if (voiceEnabled && draft.isEmpty()) Box(Modifier.matchParentSize().testTag("plugin-chat-input-hold")
            .dictationGesture(chat.conversationId, { begin() }, { dispatchVoice(it) }, { dictating = false }, shortTap = { askForFocus() }))
        }
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatComposerControls(chat, enabled, owner, Modifier.weight(1f), invoke)
            if (chat.imageAction != null && chat.fileAction != null) Box {
                IconButton(onClick = feedbackClick { attachments = true }, enabled = enabled,
                    modifier = Modifier.iconLongAction(enabled && chat.draftImages.size < 4) { invoke(requireNotNull(chat.imageAction), mapOf("source" to "camera")) }) {
                    Icon(Icons.Default.AddCircleOutline, uiText("chat.attach"))
                }
                DropdownMenu(attachments, { attachments = false }) {
                    DropdownMenuItem({ Text(uiText("chat.image")) }, onClick = { attachments = false; invoke(requireNotNull(chat.imageAction), emptyMap()) }, enabled = chat.draftImages.size < 4)
                    DropdownMenuItem({ Text(uiText("chat.file")) }, onClick = { attachments = false; invoke(requireNotNull(chat.fileAction), emptyMap()) }, enabled = chat.draftFiles.size < 8)
                }
            } else {
                chat.imageAction?.let { action -> IconButton(onClick = feedbackClick { invoke(action, emptyMap()) },
                modifier = Modifier.testTag("plugin-chat-image").iconLongAction(enabled && chat.draftImages.size < 4) { invoke(action, mapOf("source" to "camera")) },
                enabled = enabled && chat.draftImages.size < 4) { Icon(Icons.Default.AddPhotoAlternate, uiText("chat.image")) } }
                chat.fileAction?.let { action -> IconButton(onClick = feedbackClick { invoke(action, emptyMap()) }, enabled = enabled && chat.draftFiles.size < 8, modifier = Modifier.testTag("plugin-chat-file")) { Icon(Icons.Default.AttachFile, uiText("chat.file")) } }
            }
            val showVoice = voiceEnabled && draft.isEmpty() && !canSend
            val primary = if (showVoice) ChatPrimaryAction.VOICE else if (running && canSend) ChatPrimaryAction.QUEUE else ChatPrimaryAction.SEND
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                AnimatedButtonContent(primary) { action ->
                    if (action == ChatPrimaryAction.VOICE) {
                        val label = uiText("chat.dictation")
                        Box(Modifier.fillMaxSize().testTag("plugin-chat-voice").semantics { contentDescription = label }
                            .dictationGesture(chat.conversationId, { begin() }, { dispatchVoice(it) }, { dictating = false }), contentAlignment = Alignment.Center) {
                            Icon(if (dictating) Icons.Default.GraphicEq else Icons.Default.Mic, null,
                                tint = if (dictating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    } else {
                        val label = uiText(if (action == ChatPrimaryAction.QUEUE) "chat.queue" else "chat.send")
                        IconActionHint(label) {
                            IconButton(onClick = feedbackClick { sendAction?.let(::send) }, enabled = enabled && canSend && sendAction != null,
                                modifier = Modifier.testTag("plugin-chat-send").iconLongAction(true, null)) {
                                Icon(if (action == ChatPrimaryAction.QUEUE) Icons.AutoMirrored.Filled.PlaylistAdd else Icons.AutoMirrored.Filled.Send, label)
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(running, enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()) {
                val label = uiText("chat.stop")
                IconActionHint(label) {
                    IconButton(onClick = feedbackClick { invoke(chat.stopAction, emptyMap()) }, modifier = Modifier.testTag("plugin-chat-stop")) {
                        Icon(Icons.Default.Stop, label)
                    }
                }
            }
        }
    } } }
}
private enum class ChatPrimaryAction { SEND, VOICE, QUEUE }

private enum class ChatPress { TAP, CANCEL }

private fun Modifier.dictationGesture(id: String, start: () -> (suspend (Boolean) -> String?)?, send: (String) -> Unit,
    done: () -> Unit, shortTap: (() -> Unit)? = null): Modifier = pointerInput(id) {
    coroutineScope {
        awaitEachGesture {
            awaitFirstDown().consume()
            val press = if (shortTap == null) null else withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation()?.let { it.consume(); ChatPress.TAP } ?: ChatPress.CANCEL
            }
            if (press == ChatPress.TAP) { shortTap?.invoke(); return@awaitEachGesture }
            if (press == ChatPress.CANCEL) return@awaitEachGesture
            val finish = start() ?: return@awaitEachGesture
            var released = false
            try { released = waitForUpOrCancellation()?.let { it.consume(); true } ?: false }
            finally {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try { finish(released)?.let(send) } finally { done() }
                }
            }
        }
    }
}
