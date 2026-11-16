@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import com.kigtts.next.ui.components.reorder.*

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.plugins.SlotActions
import com.kigtts.plugin.api.UiSlot

@Composable
fun CategoryTabs(state: AppState, categories: List<String>, model: AppViewModel, modifier: Modifier = Modifier,
    editing: Boolean = false, addGroup: () -> Unit = {}, editGroup: (String) -> Unit = {}) {
    val list = rememberLazyListState()
    var viewportRestore by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(categories) {
        viewportRestore?.let { (index, offset) -> list.requestScrollToItem(index, offset); viewportRestore = null }
    }
    val categoryIcons by model.quickTexts.categoryIcons.collectAsStateWithLifecycle()
    val quickSorting by model.quickSort.enabled.collectAsStateWithLifecycle()
    val sources = categories.map { category -> key(category) { remember { MutableInteractionSource() } } }
    val pressed = sources.map { it.collectIsPressedAsState().value }
    val feedback = rememberDragFeedback()
    val accessibleFeedback = feedbackClick {}
    val drag = rememberReorderDrag({ list.layoutInfo.visibleItemsInfo.mapNotNull { item ->
        (item.key as? String)?.takeIf { it.startsWith("category/") }?.removePrefix("category/")?.takeIf { it in categories }?.let {
            ReorderTarget(it, Rect(item.offset.toFloat(), 0f, (item.offset + item.size).toFloat(), list.layoutInfo.viewportSize.height.toFloat()))
        }
    } }, horizontalOnly = true, animateDrop = true, feedback = feedback, move = { from, to ->
        if (viewportRestore == null) viewportRestore = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
        val before = model.categories.value
        model.quickTexts.moveCategory(from, to)
        if (model.categories.value != before) feedback.step()
    })
    val drop = rememberReorderDrop(drag)
    val editingNow by rememberUpdatedState(editing)
    var menu by remember { mutableStateOf<QuickTextMenuTarget?>(null) }
    var menuEdit by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.selectedCategory,categories,drag.key) {
        if(drag.key!=null)return@LaunchedEffect
        val index=categories.indexOf(state.selectedCategory)
        if(index<0)return@LaunchedEffect
        val visible=list.layoutInfo.visibleItemsInfo.firstOrNull { it.key=="category/"+state.selectedCategory }
        if(visible==null || visible.offset<0 || visible.offset+visible.size>list.layoutInfo.viewportSize.width) {
            list.animateScrollToItem(index)
        }
    }
    Row(modifier.fillMaxWidth().height(48.dp).homePreviewRegion("category-bar").snackbarAnchor(!state.subtitleExpanded || editing).testTag("category-tab-bar"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SlotActions(UiSlot.TAB_START, model.registry, model::invoke, context = model.context(), revision = state.pluginRevision)
        LazyRow(Modifier.weight(1f).testTag("category-tabs").reorderGestures(drag,
            onStart = { editingNow || model.quickSort.begin() }, onStationaryHold = { menu = it.menuTarget(group = true) }),
            state = list, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(categories, key = { _, name -> "category/" + name }) { index, category ->
                val previous = categories.getOrNull(index - 1); val next = categories.getOrNull(index + 1)
                val semantics = Modifier.semantics {
                    if (editing || quickSorting == true) customActions = listOfNotNull(
                        previous?.let { CustomAccessibilityAction("向前移动分组") { model.quickTexts.moveCategory(category, it); accessibleFeedback(); true } },
                        next?.let { CustomAccessibilityAction("向后移动分组") { model.quickTexts.moveCategory(category, it); accessibleFeedback(); true } })
                }
                val selected = state.selectedCategory == category
                ElasticTab(selected, { if (editing && selected) editGroup(category) else model.category(category) }, sources[index],
                    CornerNeighbors(start = index > 0, end = index < categories.lastIndex || editing),
                    pressed.getOrElse(index - 1) { false } || pressed.getOrElse(index + 1) { false },
                    Modifier.testTag("category-" + category).homePreviewRegion((if (selected) "tab-selected/" else "tab/") + category).then(semantics)
                        .then(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null,
                            placementSpec = if (drag.key == category) null else MaterialTheme.motionScheme.defaultSpatialSpec())
                            .reorderTranslation(category, drag, drop)),
                    icon = {
                        Icon((categoryIcons[category] ?: CategoryIcon.defaultFor(category)).vector(),
                            "分组图标：" + (categoryIcons[category] ?: CategoryIcon.defaultFor(category)).label,
                            Modifier.size(16.dp).testTag("category-icon-" + category))
                    }, label = { Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = if (!editing) null else {
                        { Box(Modifier.size(16.dp).testTag("category-grip-" + category).reorderGrip(category, drag)) {
                            AnimatedButtonContent(selected) { active ->
                                Icon(if (active) Icons.Default.Edit else Icons.Default.DragIndicator,
                                    if (active) "编辑分组 " + category else "拖动分组 " + category,
                                    Modifier.size(16.dp))
                            }
                        } }
                    }, dragging = drag.key == category)
            }
            if (editing) item(key = "add-category") {
                val source = remember { MutableInteractionSource() }
                ElasticTab(false, addGroup, source, CornerNeighbors(start = categories.isNotEmpty()), false,
                    Modifier.testTag("add-category"), icon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }, label = { Text(uiText("quick.add_group")) })
            }
        }
        SlotActions(UiSlot.TAB_END, model.registry, model::invoke, context = model.context(), revision = state.pluginRevision)
    }
    ReorderAutoScroll(drag, { list.layoutInfo.viewportSize.width.toFloat() }, { list.scrollBy(it) }, horizontal = true)
    QuickTextContextMenu(menu, model, { menu = null }, { if (editing) editGroup(it) else menuEdit = it })
    menuEdit?.let { EditCategoryDialog(it, categories, model) { menuEdit = null } }
}
