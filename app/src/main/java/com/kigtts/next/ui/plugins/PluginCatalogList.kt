package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.management.ResourceAvatar
import com.kigtts.next.ui.management.InlineProgressButton
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.plugin.api.*
import java.io.File

/** Catalog entries show artwork and useful descriptions without rankings or recommendation sections. */
@Composable internal fun PluginCatalogList(list: ManagementList, busy: Boolean, resolve: (PluginResource) -> File?,
    invoke: (String, Map<String, String>) -> Unit, header: @Composable ColumnScope.() -> Unit) {
    val enabled = !busy && LocalPageActive.current
    LazyColumn(Modifier.fillMaxSize().testTag("managed-list/" + list.id), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("catalog-header") { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = header) }
        if (list.items.isEmpty()) item("empty") { Text(list.emptyText, Modifier.padding(vertical = 24.dp)) }
        list.items.groupBy { it.group }.forEach { (group, entries) ->
            if (group.isNotBlank()) item("category/$group") { Text(group, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(entries, key = { it.id }) { item ->
                Surface(onClick = feedbackClick { item.primaryAction?.let { invoke(it, emptyMap()) } }, enabled = enabled,
                    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().testTag("managed-item/" + item.id)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ResourceAvatar(item, resolve, 64.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        val action = list.itemActions[item.id]
                        if (action != null) InlineProgressButton(item.id, action, enabled, invoke)
                        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp))
                        if (item.actions.isNotEmpty()) CatalogItemMenu(item, enabled, invoke)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CatalogItemMenu(item: ManagementItem, enabled: Boolean, invoke: (String, Map<String, String>) -> Unit) {
    var open by remember(item.id) { mutableStateOf(false) }
    Box {
        TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface) { Text("更多操作") } }, state = rememberTooltipState()) {
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
