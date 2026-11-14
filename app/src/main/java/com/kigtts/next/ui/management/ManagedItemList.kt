@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.management

import com.kigtts.next.ui.components.reorder.*

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.EmptyContent
import com.kigtts.next.ui.components.rememberDragFeedback
import com.kigtts.next.ui.navigation.RegisterPageLoading
import com.kigtts.plugin.api.*
import java.io.File
import kotlin.math.roundToInt

/** Shared virtualized list with stable container gestures, drop animation and one persisted commit. */
@Composable
fun ManagedItemList(list: ManagementList, busy: Boolean = false, resolve: (PluginResource) -> File? = { null },
    invoke: (String, Map<String, String>) -> Unit, compactActions: Boolean = false, header: @Composable ColumnScope.() -> Unit = {}) {
    com.kigtts.next.ui.components.ReadablePage {
    val columns = if (maxWidth >= 960.dp) 2 else 1
    val lazy = rememberLazyGridState()
    RegisterPageLoading(busy)
    val density = androidx.compose.ui.platform.LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    var revealedItem by remember(list.id) { mutableStateOf<String?>(null) }
    var heldItem by remember(list.id) { mutableStateOf<String?>(null) }
    var holdRequest by remember(list.id) { mutableIntStateOf(0) }
    val topInset = if (list.filters.isEmpty()) 16.dp else 4.dp
    val horizontalPadding = with(density) { 16.dp.toPx() }
    val external = list.items.map { it.id }
    var order by remember(list.id) { mutableStateOf(external) }
    val data = list.items.associateBy { it.id }
    val feedback = rememberDragFeedback()
    val pageActive = com.kigtts.next.ui.navigation.LocalPageActive.current
    val interactive = pageActive && !busy
    val drag = rememberReorderDrag({ lazy.layoutInfo.visibleItemsInfo.mapNotNull { info ->
        (info.key as? String)?.takeIf { it in data }?.let { ReorderTarget(it,
            Rect(info.offset.x + horizontalPadding, (info.offset.y + lazy.layoutInfo.beforeContentPadding).toFloat(), info.offset.x + info.size.width + horizontalPadding,
                (info.offset.y + info.size.height + lazy.layoutInfo.beforeContentPadding).toFloat())) }
    } }, animateDrop = true, verticalOnly = columns == 1, feedback = feedback) { from, to ->
        if (data[from]?.let { a -> data[to]?.let { b -> managedItemsConnected(a, b) } } == true) {
            order = ItemOrder.move(order, from, to); feedback.step()
        }
    }
    var owned by remember { mutableStateOf(false) }
    LaunchedEffect(external) { if (drag.key == null) order = external }
    LaunchedEffect(drag.key) {
        if (drag.key != null) { owned = true }
        else if (owned) {
            owned = false
            if (order != external) list.reorderAction?.let { invoke(it, mapOf("order" to PluginJson.encodeToString(ItemOrder.reconcile(order, external)))) }
        }
    }
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { if (!busy && wasBusy && drag.key == null) order = external; wasBusy = busy }
    val offset by rememberReorderDrop(drag)
    LaunchedEffect(lazy.isScrollInProgress, drag.key) {
        if (lazy.isScrollInProgress || drag.key != null) revealedItem = null
    }
    val elevation by animateDpAsState(if (drag.key != null && !drag.settling) 8.dp else 0.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "managed-drop-elevation")
    fun moveAccessible(id: String, delta: Int) {
        val position = order.indexOf(id); val target = order.getOrNull(position + delta) ?: return
        if (data[id]?.let { a -> data[target]?.let { b -> managedItemsConnected(a, b) } } != true) return
        order = ItemOrder.move(order, id, target)
        list.reorderAction?.let { invoke(it, mapOf("order" to PluginJson.encodeToString(order))) }
    }
    Box(Modifier.fillMaxSize().testTag("managed-list/" + list.id).then(if (list.reorderAction != null && interactive)
        Modifier.reorderGestures(drag, onStationaryHold = { target ->
            heldItem = target.key; revealedItem = target.key; holdRequest++
        }, onDragStart = { revealedItem = null; heldItem = null }) else Modifier)) {
        val pagePadding = com.kigtts.next.ui.navigation.pageContentPadding()
        val sections = managedItemSections(order.mapNotNull(data::get))
        LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize(), state = lazy, contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = topInset,
            bottom = pagePadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item("management-header", span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.onSizeChanged { headerHeight = it.height }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                header()
                if (list.filters.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list.filters, key = { it.id }) { filter -> FilterChip(selected = filter.selected,
                    onClick = feedbackClick { if(interactive)invoke(filter.action, emptyMap()) }, label = { Text(filter.label) }) }
                }
            } }
            sections.forEachIndexed { sectionIndex, section ->
            if (sectionIndex > 0) item("management-gap/${section.first().id}", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(12.dp).testTag("managed-section-gap/${section.first().id}"))
            }
            itemsIndexed(section, key = { _, item -> item.id }) { index, item ->
                ManagedItemRow(item, Modifier.animateItem(fadeInSpec = tween(180), fadeOutSpec = tween(120),
                        placementSpec = if (drag.key == item.id) null else MaterialTheme.motionScheme.defaultSpatialSpec())
                        .then(if (drag.key == item.id) Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics { } else Modifier),
                    Modifier.reorderGrip(item.id, drag), list.reorderAction != null, false, pageActive, resolve,
                    { action, inputs -> if (interactive) invoke(action, inputs) }, { if(interactive)moveAccessible(item.id, it) },
                    catalog = list.style == ManagementListStyle.CATALOG, trailingAction = list.itemActions[item.id],
                    compactActions = compactActions,
                    revealActive = revealedItem == item.id, activateReveal = { revealedItem = item.id },
                    holdRequest = if (heldItem == item.id) holdRequest else 0,
                    position = managedGridPosition(section, index, columns))
            }
            }
        }
        EmptyContent(list.items.isEmpty() && !busy, Icons.AutoMirrored.Filled.ListAlt, list.emptyText,
            Modifier.padding(top = with(density) { headerHeight.toDp() } + topInset, bottom = 24.dp))
        val draggedItem = drag.key?.let(data::get)
        val ordered = sections.firstOrNull { section -> section.any { it.id == draggedItem?.id } }.orEmpty()
        val draggedPosition = ordered.indexOfFirst { it.id == draggedItem?.id }
        if (draggedItem != null) ManagedItemRow(draggedItem,
            Modifier.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }.width(with(density) { drag.floatingBounds.width.toDp() })
                .height(with(density) { drag.floatingBounds.height.toDp() }).clearAndSetSemantics { testTag = "managed-drag/" + draggedItem.id },
            Modifier, list.reorderAction != null, true, pageActive, resolve, { _, _ -> }, {}, shadowElevation = elevation,
            catalog = list.style == ManagementListStyle.CATALOG, trailingAction = list.itemActions[draggedItem.id],
            compactActions = compactActions,
            position = if (draggedPosition >= 0) managedGridPosition(ordered, draggedPosition, columns) else com.kigtts.next.ui.settings.SettingPosition.SINGLE)
        ReorderAutoScroll(drag, { lazy.layoutInfo.viewportSize.height.toFloat() }, { lazy.scrollBy(it) })
    }
    }
}
