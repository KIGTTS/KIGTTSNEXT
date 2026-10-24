package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ReadablePage
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.plugin.api.*
import kotlinx.coroutines.launch
import java.io.File

/** Retain visited categories while the plugin asynchronously provides the next page. */
@Composable
internal fun PluginCatalogList(list: ManagementList, busy: Boolean, resolve: (PluginResource) -> File?,
    invoke: (String, Map<String, String>) -> Unit, header: @Composable ColumnScope.() -> Unit) {
    val active = LocalPageActive.current
    if (list.filters.size < 2) {
        Column(Modifier.fillMaxSize()) {
            CatalogFixedHeader(list, active, { action, inputs -> if (!busy) invoke(action, inputs) }, header)
            Box(Modifier.weight(1f)) { PluginCatalogPane(list, busy, resolve, invoke) }
        }
        return
    }
    val ids = list.filters.map { it.id }
    key(ids) {
        val selected = list.filters.indexOfFirst { it.selected }.coerceAtLeast(0)
        val pager = rememberPagerState(initialPage = selected) { ids.size }
        val snapshots = remember { mutableStateMapOf<String, ManagementList>() }
        var requested by remember { mutableIntStateOf(selected) }
        val scope = rememberCoroutineScope()
        val latestInvoke by rememberUpdatedState(invoke)
        SideEffect {
            snapshots[ids[selected]] = list
            // Bound retained snapshots, without evicting the visible or destination category.
            if (snapshots.size > 5) snapshots.keys.firstOrNull { it != ids[selected] && it != ids[pager.currentPage] }?.let(snapshots::remove)
        }
        LaunchedEffect(pager.settledPage, busy) {
            val target = pager.settledPage
            if (!busy && requested != target) {
                requested = target
                if (target != selected) latestInvoke(list.filters[target].action, emptyMap())
            }
        }
        Column(Modifier.fillMaxSize()) {
        CatalogFixedHeader(list.copy(filters = list.filters.mapIndexed { i, filter -> filter.copy(selected = i == pager.currentPage) }),
            active, { action, inputs ->
                val target = list.filters.indexOfFirst { it.action == action }
                if (target >= 0) scope.launch { pager.animateScrollToPage(target) }
                else if (!busy) latestInvoke(action, inputs)
            }, header)
        HorizontalPager(pager, Modifier.weight(1f).testTag("catalog-pages"), key = { ids[it] }, beyondViewportPageCount = 1) { index ->
            val snapshot = if (index == selected) list else snapshots[ids[index]]
            val loading = snapshot == null
            val content = (snapshot ?: list.copy(id = "pending/${ids[index]}", items = emptyList(), itemActions = emptyMap(), loadMore = null))
                .copy(filters = list.filters.mapIndexed { i, filter -> filter.copy(selected = i == index) })
            PluginCatalogPane(content, busy || index != selected || pager.isScrollInProgress, resolve,
                invoke = { action, inputs -> if (index == selected && !pager.isScrollInProgress) latestInvoke(action, inputs) },
                loading = loading)
        }
        }
    }
}

/** A single header lives outside the pager: dragging a category never translates or recreates it. */
@Composable
private fun CatalogFixedHeader(list: ManagementList, active: Boolean,
    invoke: (String, Map<String, String>) -> Unit, header: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("catalog-fixed-header"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                header()
                CatalogShortcuts(list.shortcuts, wide, active, invoke)
                CatalogTabs(list.filters, active, invoke)
            }
        }
    }
}
