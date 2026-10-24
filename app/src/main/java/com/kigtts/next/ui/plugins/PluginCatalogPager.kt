package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kigtts.plugin.api.*
import kotlinx.coroutines.launch
import java.io.File

/** Retain visited categories while the plugin asynchronously provides the next page. */
@Composable
internal fun PluginCatalogList(list: ManagementList, busy: Boolean, resolve: (PluginResource) -> File?,
    invoke: (String, Map<String, String>) -> Unit, header: @Composable ColumnScope.() -> Unit) {
    if (list.filters.size < 2) { PluginCatalogPane(list, busy, resolve, invoke, header = header); return }
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
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { ids[it] }, beyondViewportPageCount = 1) { index ->
            val snapshot = if (index == selected) list else snapshots[ids[index]]
            val loading = snapshot == null
            val content = (snapshot ?: list.copy(id = "pending/${ids[index]}", items = emptyList(), itemActions = emptyMap(), loadMore = null))
                .copy(filters = list.filters.mapIndexed { i, filter -> filter.copy(selected = i == index) })
            PluginCatalogPane(content, busy || index != selected || pager.isScrollInProgress, resolve,
                invoke = { action, inputs -> if (index == selected && !pager.isScrollInProgress) latestInvoke(action, inputs) },
                loading = loading,
                selectFilter = { id -> ids.indexOf(id).takeIf { it >= 0 }?.let { target -> scope.launch { pager.animateScrollToPage(target) } } },
                header = header)
        }
    }
}
