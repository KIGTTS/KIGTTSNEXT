package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.settings.SettingsGroup
import com.kigtts.plugin.api.ManagementItem
import com.kigtts.plugin.api.ManagementList
import com.kigtts.plugin.api.PluginResource
import java.io.File

/** Inner corners stay small where cells meet, including a two-column section's last partial row. */
internal fun catalogGroupShape(index: Int, count: Int, columns: Int, outer: CornerBasedShape): RoundedCornerShape {
    val row = index / columns
    val column = index % columns
    val lastRow = (count - 1) / columns
    val lastColumn = minOf(columns - 1, count - row * columns - 1)
    val inner = CornerSize(4.dp)
    return RoundedCornerShape(
        topStart = if (row == 0 && column == 0) outer.topStart else inner,
        topEnd = if (row == 0 && column == lastColumn) outer.topEnd else inner,
        bottomStart = if (row == lastRow && column == 0) outer.bottomStart else inner,
        bottomEnd = if (row == lastRow && column == lastColumn) outer.bottomEnd else inner)
}

/** A homepage section only contains a few summary rows. The surrounding grid still mounts sections lazily. */
@Composable
internal fun PluginCatalogSection(title: String, entries: List<ManagementItem>, columns: Int,
    list: ManagementList, active: Boolean, canInvoke: Boolean, resolve: (PluginResource) -> File?,
    invoke: (String, Map<String, String>) -> Unit, modifier: Modifier = Modifier) {
    SettingsGroup(title, modifier = modifier.testTag("catalog-section-card/$title"),
        headerAction = list.sectionActions[title]?.let { action -> {
            CatalogSectionAction(action.label) { if (canInvoke) invoke(action.id, emptyMap()) }
        } }) {
        Column(Modifier.testTag("catalog-section/$title"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            entries.chunked(columns).forEachIndexed { row, cells ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    cells.forEachIndexed { column, item ->
                        PluginCatalogEntry(item, list.itemActions[item.id], active, canInvoke, resolve, invoke,
                            Modifier.weight(1f).fillMaxHeight(),
                            catalogGroupShape(row * columns + column, entries.size, columns, MaterialTheme.shapes.small))
                    }
                    repeat(columns - cells.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable internal fun CatalogSectionAction(label: String, onClick: () -> Unit) {
        Row(Modifier.clickable(role = Role.Button, onClick = feedbackClick(onClick))
            .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
}
