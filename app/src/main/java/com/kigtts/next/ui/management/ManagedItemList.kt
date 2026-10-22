@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.management

import com.kigtts.next.ui.components.reorder.*

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.rememberDragFeedback
import com.kigtts.next.ui.navigation.RegisterPageLoading
import com.kigtts.plugin.api.*
import java.io.File
import kotlin.math.roundToInt

/** Shared virtualized list with stable container gestures, drop animation and one persisted commit. */
@Composable
fun ManagedItemList(list: ManagementList, busy: Boolean = false, resolve: (PluginResource) -> File? = { null },
    invoke: (String, Map<String, String>) -> Unit, header: @Composable ColumnScope.() -> Unit = {}) {
    val lazy = rememberLazyListState()
    RegisterPageLoading(busy)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val padding = with(density) { 16.dp.toPx() }
    val external = list.items.map { it.id }
    var order by remember(list.id) { mutableStateOf(external) }
    val data = list.items.associateBy { it.id }
    val feedback = rememberDragFeedback()
    val interactive = !busy && com.kigtts.next.ui.navigation.LocalPageActive.current
    val drag = rememberReorderDrag({ lazy.layoutInfo.visibleItemsInfo.mapNotNull { info ->
        (info.key as? String)?.takeIf { it in data }?.let { ReorderTarget(it,
            Rect(padding, (info.offset + lazy.layoutInfo.beforeContentPadding).toFloat(), lazy.layoutInfo.viewportSize.width.toFloat() - padding,
                (info.offset + info.size + lazy.layoutInfo.beforeContentPadding).toFloat())) }
    } }, animateDrop = true, verticalOnly = true) { from, to ->
        if (data[from]?.group == data[to]?.group) { order = ItemOrder.move(order, from, to); feedback.step() }
    }
    var owned by remember { mutableStateOf(false) }
    LaunchedEffect(external) { if (drag.key == null) order = external }
    LaunchedEffect(drag.key) {
        if (drag.key != null) { owned = true; feedback.start(0f) }
        else if (owned) {
            owned = false; feedback.finish()
            if (order != external) list.reorderAction?.let { invoke(it, mapOf("order" to PluginJson.encodeToString(ItemOrder.reconcile(order, external)))) }
        }
    }
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { if (!busy && wasBusy && drag.key == null) order = external; wasBusy = busy }
    val offset by rememberReorderDrop(drag)
    val elevation by animateDpAsState(if (drag.key != null && !drag.settling) 8.dp else 0.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "managed-drop-elevation")
    fun moveAccessible(id: String, delta: Int) {
        val position = order.indexOf(id); val target = order.getOrNull(position + delta) ?: return
        if (data[id]?.group != data[target]?.group) return
        order = ItemOrder.move(order, id, target)
        list.reorderAction?.let { invoke(it, mapOf("order" to PluginJson.encodeToString(order))) }
    }
    Box(Modifier.fillMaxSize().testTag("managed-list/" + list.id).then(if (list.reorderAction != null && interactive) Modifier.reorderGestures(drag) else Modifier)) {
        LazyColumn(Modifier.fillMaxSize(), state = lazy, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item("management-header") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header()
                if (list.filters.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list.filters, key = { it.id }) { filter -> FilterChip(selected = filter.selected,
                    onClick = feedbackClick { if(interactive)invoke(filter.action, emptyMap()) }, label = { Text(filter.label) }) }
                }
                AnimatedVisibility(list.items.isEmpty(), enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(220))) {
                    Text(list.emptyText, Modifier.padding(vertical = 16.dp))
                }
            } }
            items(order.mapNotNull(data::get), key = { it.id }) { item ->
                ManagedItemRow(item, Modifier.animateItem(fadeInSpec = tween(180), fadeOutSpec = tween(120),
                        placementSpec = if (drag.key == item.id) null else MaterialTheme.motionScheme.defaultSpatialSpec())
                        .then(if (drag.key == item.id) Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics { } else Modifier),
                    Modifier.reorderGrip(item.id, drag), list.reorderAction != null, false, interactive, resolve, invoke, { if(interactive)moveAccessible(item.id, it) },
                    catalog = list.style == ManagementListStyle.CATALOG, trailingAction = list.itemActions[item.id])
            }
        }
        val active = drag.key?.let(data::get)
        if (active != null) ManagedItemRow(active,
            Modifier.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }.width(with(density) { drag.floatingBounds.width.toDp() })
                .height(with(density) { drag.floatingBounds.height.toDp() }).clearAndSetSemantics { testTag = "managed-drag/" + active.id },
            Modifier, list.reorderAction != null, true, true, resolve, { _, _ -> }, {}, shadowElevation = elevation)
        ReorderAutoScroll(drag, { lazy.layoutInfo.viewportSize.height.toFloat() }, { lazy.scrollBy(it) })
    }
}
