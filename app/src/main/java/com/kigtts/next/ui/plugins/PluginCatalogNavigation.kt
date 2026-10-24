@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.vector
import com.kigtts.next.ui.components.ChoiceButtonGroup
import com.kigtts.next.ui.components.keepColorsWhenLocked
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.first

@Composable internal fun CatalogShortcuts(actions: List<PageAction>, wide: Boolean, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit) {
    if (actions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.chunked(if (wide) actions.size else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { action ->
                    FilledTonalButton(onClick = feedbackClick { invoke(action.id, emptyMap()) }, enabled = enabled,
                        colors = ButtonDefaults.filledTonalButtonColors().keepColorsWhenLocked(),
                        shapes = ButtonDefaults.shapes(RoundedCornerShape(24.dp), RoundedCornerShape(14.dp)),
                        contentPadding = PaddingValues(16.dp), modifier = Modifier.weight(1f).heightIn(min = 80.dp)
                            .testTag("catalog-shortcut/" + action.id)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(action.icon.vector(action.symbol), null, Modifier.size(32.dp))
                            Text(action.label, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun CatalogTabs(filters: List<ManagementFilter>, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit) {
    if (filters.isEmpty()) return
    val selected = filters.firstOrNull { it.selected }?.id ?: filters.first().id
    val selectedIndex = filters.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    var dragDistance by remember(filters, selected) { mutableFloatStateOf(0f) }
    fun switchTab(delta: Int) {
        filters.getOrNull((selectedIndex + delta).coerceIn(0, filters.lastIndex))?.let { target ->
            if (target.id != selected) invoke(target.action, emptyMap())
        }
    }
    Box(Modifier.fillMaxWidth().testTag("catalog-tabs")) {
        val scroll = rememberScrollState()
        Box(Modifier.horizontalScroll(scroll).pointerInput(filters, selected) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, amount -> change.consume(); dragDistance += amount },
                onDragEnd = { val distance = dragDistance; dragDistance = 0f; if (kotlin.math.abs(distance) > 48f) switchTab(if (distance < 0f) 1 else -1) },
                onDragCancel = { dragDistance = 0f })
        }) {
            ChoiceButtonGroup("插件分类", filters.map { it.id }, selected,
                label = { id -> filters.first { it.id == id }.label },
                onSelect = { id -> invoke(filters.first { it.id == id }.action, emptyMap()) },
                modifier = Modifier.graphicsLayer { translationX = (dragDistance * .12f).coerceIn(-28f, 28f) }, enabled = enabled,
                preserveColorWhenLocked = true, itemRole = Role.Tab, fitContent = true,
                icon = { id -> filters.first { it.id == id }.symbol?.let { symbol ->
                    Icon(PluginIcon.NONE.vector(symbol), null, Modifier.size(18.dp))
                } })
        }
    }
}

/** A stable token prevents duplicate requests during recomposition and fast scrolling. */
@Composable internal fun CatalogLoadMore(list: ManagementList, scroll: LazyGridState, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit) {
    val action = list.loadMore?.action
    val currentInvoke by rememberUpdatedState(invoke)
    val active by rememberUpdatedState(enabled)
    LaunchedEffect(list.id, action) {
        if (action != null) {
            snapshotFlow { active && scroll.layoutInfo.visibleItemsInfo.any { it.key == "catalog-more" } }.first { it }
            currentInvoke(action, emptyMap())
        }
    }
}

@Composable internal fun CatalogLoadingFooter() {
    Box(Modifier.fillMaxWidth().height(64.dp).testTag("catalog-load-more"), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator(Modifier.size(40.dp))
    }
}
