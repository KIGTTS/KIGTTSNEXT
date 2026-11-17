@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.animation.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.MutatePriority
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.pulltorefresh.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.EmptyContent
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.Channel

/** User scrolling owns the viewport until they explicitly return to the bottom. */
@Composable
internal fun PluginChatScreen(chat: PageBlock.Chat, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit, resolve: (PluginResource) -> java.io.File? = { null },
    historyCache: ChatHistoryCache? = null, historyKey: String = chat.conversationId,
    startVoiceInput: (() -> (suspend (Boolean) -> String?)?)? = null,
    followIme: Boolean = false,
    header: @Composable ColumnScope.() -> Unit) {
    val list = remember(chat.conversationId) { LazyListState() }
    val dispatch: (String, Map<String, String>) -> Unit = { action, inputs -> invoke(action, inputs + ("_chatConversation" to chat.conversationId)) }
    var follow by remember(chat.conversationId) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var expansionEpoch by remember(chat.conversationId) { mutableLongStateOf(0L) }
    var expansionActive by remember(chat.conversationId) { mutableStateOf(false) }
    val readingInteraction = remember(list, scope) { {
        follow = false; expansionActive = true; expansionEpoch++
        scope.launch { list.stopScroll(MutatePriority.UserInput) }
        Unit
    } }
    LaunchedEffect(expansionEpoch) {
        if (expansionEpoch > 0) { delay(500); expansionActive = false }
    }
    val dragging by list.interactionSource.collectIsDraggedAsState()
    val scrollRequests = remember(chat.conversationId) { Channel<ChatScrollRequest>(Channel.CONFLATED) }
    val cache = historyCache ?: remember { ChatHistoryCache() }
    val messages = remember(chat.messages, chat.historyTotal, chat.running, historyKey) { cache.merge(historyKey, chat) }
    val rows = remember(messages) { chatTimeline(messages) }
    val rowCount = rememberUpdatedState(rows.size)
    val earliest = messages.minOfOrNull { it.position } ?: 0
    val hasEarlier = chat.historyAction != null && earliest > 0
    var loadingBefore by remember(chat.conversationId) { mutableStateOf<Int?>(null) }
    var historyWasBusy by remember(chat.conversationId) { mutableStateOf(false) }
    fun loadEarlier() {
        if (!hasEarlier || loadingBefore != null) return
        follow = false; cache.retainEarlier(historyKey); historyWasBusy = false; loadingBefore = earliest
        dispatch(requireNotNull(chat.historyAction), mapOf("before" to earliest.toString()))
    }
    LaunchedEffect(earliest, loadingBefore, enabled) {
        if (loadingBefore != null) {
            if (!enabled) historyWasBusy = true
            if (earliest < requireNotNull(loadingBefore) || enabled && historyWasBusy) { delay(160); loadingBefore = null }
        }
    }
    LaunchedEffect(loadingBefore) { if (loadingBefore != null) { delay(10000); loadingBefore = null } }
    val pull = rememberPullToRefreshState()
    val snapDistance = with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.lineHeight.toPx() / 2 }
    val atBottom by remember(list, snapDistance) { derivedStateOf { chatBottomGap(list.layoutInfo) <= snapDistance } }
    LaunchedEffect(dragging) { if (dragging) follow = false }
    // All automatic scrolls use one serial actor. A new stream chunk cannot cancel a
    // half-finished end alignment or race the user's half-line snap.
    LaunchedEffect(list, scrollRequests) {
        for (request in scrollRequests) {
            if (dragging) continue
            if (request == ChatScrollRequest.JUMP) { expansionActive = false; follow = true }
            if (request == ChatScrollRequest.SNAP) {
                if (follow || expansionActive || list.isScrollInProgress || chatBottomGap(list.layoutInfo) > snapDistance) continue
                follow = true
            }
            if (!follow || rowCount.value == 0) continue
            withFrameNanos { }
            if (!follow || expansionActive || dragging) continue
            list.scrollToChatEnd(rowCount.value, animated = request != ChatScrollRequest.UPDATE)
        }
    }
    LaunchedEffect(list, snapDistance) {
        // Do not key/cancel this collector on isScrollInProgress: our own snap animation
        // changes that flag and would otherwise repeatedly cancel and restart itself.
        snapshotFlow { Triple(chatBottomGap(list.layoutInfo), list.isScrollInProgress, dragging) to expansionActive }.collect { (viewport, expanding) ->
            val (gap, moving, held) = viewport
            if (gap <= snapDistance && !moving && !held && !expanding && !follow) {
                scrollRequests.trySend(ChatScrollRequest.SNAP)
            }
        }
    }
    // Only new response content requests a follow scroll. Restoring follow after a user scroll
    // must not align the last row again (especially a short row or a large tool group).
    LaunchedEffect(messages.lastOrNull()) {
        if (follow && !dragging && rows.isNotEmpty()) {
            scrollRequests.trySend(ChatScrollRequest.UPDATE)
        }
    }
    ChatViewport(Modifier.fillMaxSize().testTag("plugin-chat"), followIme = followIme, overlay = {
        EmptyContent(rows.isEmpty() && !chat.running && chat.approval == null, Icons.Default.AutoAwesome,
            uiText("chat.empty"), Modifier.testTag("chat-empty"), uiText("chat.empty.hint"))
        androidx.compose.animation.AnimatedVisibility(!follow && !atBottom,
            Modifier.align(Alignment.BottomEnd).padding(16.dp), enter = fadeIn(), exit = fadeOut()) {
            SmallFloatingActionButton(onClick = feedbackClick {
                chat.historyAction?.let { dispatch(it, mapOf("latest" to "true")) }
                scrollRequests.trySend(ChatScrollRequest.JUMP)
            }, modifier = Modifier.testTag("plugin-chat-bottom")) { Icon(Icons.Default.ArrowDownward, uiText("chat.bottom")) }
        }
    }, history = {
        PullToRefreshBox(isRefreshing = loadingBefore != null, onRefresh = ::loadEarlier, state = pull, modifier = Modifier.fillMaxSize(), indicator = {
            if (hasEarlier || loadingBefore != null) PullToRefreshDefaults.LoadingIndicator(
                state = pull, isRefreshing = loadingBefore != null,
                modifier = Modifier.align(Alignment.TopCenter).testTag(if (pull.distanceFraction > 0f || loadingBefore != null) "chat-history-loading" else "chat-history-idle"))
        }) {
            LazyColumn(Modifier.fillMaxSize().testTag("plugin-chat-history"), list, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item("header") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    header()
                    if (hasEarlier || loadingBefore != null) ChatEarlierButton(false, ::loadEarlier)
                } }
                items(rows, key = { it.key }) { row ->
                    Box(Modifier.animateItem(fadeInSpec = androidx.compose.animation.core.tween(180),
                        fadeOutSpec = androidx.compose.animation.core.tween(100), placementSpec = null)) {
                        if (row.steps.isNotEmpty()) ChatExecutionGroup(row.steps, readingInteraction)
                        else row.answer?.let { ChatBubble(it, resolve, readingInteraction, showThought = false, invoke = dispatch) }
                    }
                }
            }

        }
    }, composer = {
        ChatInputBar(chat, enabled, resolve, dispatch, historyKey.substringBefore('/'), startVoiceInput) { expansionActive = false; follow = true }
    })
}
private enum class ChatScrollRequest { UPDATE, SNAP, JUMP }
internal fun chatBottomGap(info: LazyListLayoutInfo): Float {
    if (info.totalItemsCount == 0) return 0f
    val last = info.visibleItemsInfo.lastOrNull() ?: return Float.POSITIVE_INFINITY
    if (last.index != info.totalItemsCount - 1) return Float.POSITIVE_INFINITY
    return (last.offset + last.size + info.afterContentPadding - info.viewportEndOffset).coerceAtLeast(0).toFloat()
}
