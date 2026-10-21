@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.rememberDragFeedback
import com.kigtts.next.ui.components.reorder.*
import com.kigtts.plugin.api.*
import kotlinx.serialization.encodeToString

@Composable internal fun ChatQueuePane(chat: PageBlock.Chat, enabled: Boolean, invoke: (String, Map<String, String>) -> Unit) {
    val keys = chat.queued.map { it.id }; val messages = chat.queued.associateBy { it.id }
    var order by remember(chat.conversationId) { mutableStateOf(keys) }
    val list = rememberLazyListState(); val feedback = rememberDragFeedback()
    val action = chat.queued.firstNotNullOfOrNull { it.reorderAction }
    val drag = rememberReorderDrag({ list.layoutInfo.visibleItemsInfo.mapNotNull { item ->
        (item.key as? String)?.takeIf { it in messages }?.let { ReorderTarget(it,
            Rect(0f, item.offset.toFloat(), list.layoutInfo.viewportSize.width.toFloat(), (item.offset + item.size).toFloat())) }
    } }, verticalOnly = true, animateDrop = true, feedback = feedback) { from, to ->
        order = ItemOrder.move(order, from, to); feedback.step()
    }
    val drop = rememberReorderDrop(drag)
    var owned by remember { mutableStateOf(false) }
    LaunchedEffect(keys) { if (drag.key == null) order = keys else order = ItemOrder.reconcile(order, keys) }
    LaunchedEffect(drag.key) {
        if (drag.key != null) owned = true
        else if (owned) {
            owned = false
            if (order != keys && order.toSet() == keys.toSet()) action?.let { invoke(it, mapOf("order" to PluginJson.encodeToString(order))) }
        }
    }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(8.dp)) {
            Text("待发送 · ${keys.size} 条", style = MaterialTheme.typography.labelMedium)
            Box(Modifier.fillMaxWidth().height((minOf(keys.size, 3) * 48).dp)
                .then(if (enabled && action != null) Modifier.reorderGestures(drag) else Modifier)) {
                LazyColumn(Modifier.fillMaxSize().testTag("chat-queue"), state = list) {
                    items(order.mapNotNull(messages::get), key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth().height(48.dp).animateItem(fadeInSpec = null, fadeOutSpec = null,
                            placementSpec = if (drag.key == item.id) null else MaterialTheme.motionScheme.fastSpatialSpec())
                            .reorderTranslation(item.id, drag, drop).testTag("chat-queued/" + item.id), verticalAlignment = Alignment.CenterVertically) {
                            if (action != null) Icon(Icons.Default.DragIndicator, "拖动排序", Modifier.size(36.dp).reorderGrip(item.id, drag).testTag("chat-queue-grip/" + item.id))
                            Text(item.text, Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                            item.steerAction?.let { steer -> TextButton(onClick = feedbackClick { invoke(steer, emptyMap()) }, enabled = enabled,
                                modifier = Modifier.testTag("chat-queue-steer/" + item.id)) { Icon(Icons.Default.SubdirectoryArrowRight, null, Modifier.size(16.dp)); Text("插队") } }
                            IconButton(onClick = feedbackClick { invoke(item.removeAction, emptyMap()) }, enabled = enabled, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, "移除待发送消息") }
                        }
                    }
                }
                ReorderAutoScroll(drag, { list.layoutInfo.viewportSize.height.toFloat() }, { list.scrollBy(it) })
            }
        }
    }
}
