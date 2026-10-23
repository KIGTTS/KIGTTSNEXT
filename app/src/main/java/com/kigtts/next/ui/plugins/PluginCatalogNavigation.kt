@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.vector
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
    val selected = filters.indexOfFirst { it.selected }.coerceAtLeast(0)
    PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary,
        divider = {}, modifier = Modifier.testTag("catalog-tabs")) {
        filters.forEach { filter -> Tab(selected = filter.selected, enabled = enabled,
            onClick = feedbackClick { invoke(filter.action, emptyMap()) }, text = { Text(filter.label) },
            selectedContentColor = MaterialTheme.colorScheme.primary, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant) }
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
