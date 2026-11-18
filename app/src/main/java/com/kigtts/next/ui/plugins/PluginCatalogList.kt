package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.ReadablePage
import com.kigtts.next.ui.components.EmptyContent
import com.kigtts.next.ui.management.ResourceAvatar
import com.kigtts.next.ui.management.InlineProgressButton
import com.kigtts.next.ui.management.ManagedItemHeading
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.plugin.api.*
import java.io.File

/** Catalog entries show artwork and useful descriptions without rankings or recommendation sections. */
@Composable internal fun PluginCatalogPane(list: ManagementList, busy: Boolean, resolve: (PluginResource) -> File?,
    invoke: (String, Map<String, String>) -> Unit, loading: Boolean = false) {
    // Keep the catalog controls visually stable while a page request is in flight;
    // actions are gated below, so the tab group does not flash into a disabled state.
    val active = LocalPageActive.current
    val canInvoke = active && !busy
    val scroll = key(list.id) { rememberLazyGridState() }
    CatalogLoadMore(list, scroll, canInvoke, invoke)
    ReadablePage {
    val wide = maxWidth >= 600.dp
    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(columns = GridCells.Fixed(if (wide) 2 else 1), Modifier.fillMaxSize().testTag("managed-list/" + list.id),
        state = scroll, contentPadding = com.kigtts.next.ui.navigation.pageContentPadding(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (loading) item("category-loading", span = { GridItemSpan(maxLineSpan) }) { CatalogLoadingFooter() }
        list.items.groupBy { it.group }.forEach { (group, entries) ->
            if (group.isNotBlank() && list.sectionActions.isNotEmpty()) {
                item("section/$group", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.animateItem(fadeInSpec = tween(180), placementSpec = tween(200), fadeOutSpec = tween(120))) {
                        PluginCatalogSection(group, entries, if (wide) 2 else 1, list, active, canInvoke, resolve, invoke)
                    }
                }
                return@forEach
            }
            if (group.isNotBlank()) item("category/$group", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.sectionActions[group]?.let { action -> TextButton(feedbackClick { if (canInvoke) invoke(action.id, emptyMap()) }) {
                        Text(action.label); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
                    } }
                }
            }
            items(entries, key = { it.id }) { item ->
                PluginCatalogEntry(item, list.itemActions[item.id], active, canInvoke, resolve, invoke,
                    Modifier.animateItem(fadeInSpec = tween(180), placementSpec = tween(200), fadeOutSpec = tween(120)))
            }
        }
        if (list.loadMore != null) item("catalog-more", span = { GridItemSpan(maxLineSpan) }) { CatalogLoadingFooter() }
    }
    EmptyContent(list.items.isEmpty() && !loading && !busy && list.loadMore == null,
        Icons.Default.Storefront, list.emptyText, Modifier.padding(com.kigtts.next.ui.navigation.pageContentPadding()))
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CatalogItemMenu(item: ManagementItem, enabled: Boolean, invoke: (String, Map<String, String>) -> Unit) {
    var open by remember(item.id) { mutableStateOf(false) }
    Box {
        TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface) { Text(uiText("overlay.row.more")) } }, state = rememberTooltipState()) {
            IconButton(onClick = feedbackClick { open = true }, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, "更多操作")
            }
        }
        DropdownMenu(open, { open = false }) {
            item.actions.forEach { action -> DropdownMenuItem(text = { Text(action.label) },
                onClick = feedbackClick { open = false; invoke(action.id, emptyMap()) }) }
        }
    }
}
