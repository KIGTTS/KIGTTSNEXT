package com.kigtts.next.ui.management

import com.kigtts.next.i18n.uiText

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.plugin.api.*

internal fun managedItemPosition(items: List<ManagementItem>, index: Int): SettingPosition {
    val item = items[index]
    val before = items.getOrNull(index - 1)?.let { managedItemsConnected(item, it) } == true
    val after = items.getOrNull(index + 1)?.let { managedItemsConnected(item, it) } == true
    return when { before && after -> SettingPosition.MIDDLE; before -> SettingPosition.LAST
        after -> SettingPosition.FIRST; else -> SettingPosition.SINGLE }
}

internal fun managedItemsConnected(first: ManagementItem, second: ManagementItem) =
    first.group == second.group && first.pinned == second.pinned

/** Each section starts a fresh grid row, including on tablets with an odd pinned count. */
internal fun managedItemSections(items: List<ManagementItem>): List<List<ManagementItem>> {
    val sections = mutableListOf<MutableList<ManagementItem>>()
    items.forEach { item ->
        if (sections.lastOrNull()?.lastOrNull()?.let { managedItemsConnected(it, item) } != true) sections += mutableListOf<ManagementItem>()
        sections.last() += item
    }
    return sections
}

internal fun managedGridPosition(section: List<ManagementItem>, index: Int, columns: Int): SettingPosition =
    managedItemPosition(section.filterIndexed { i, _ -> i % columns == index % columns }, index / columns)

/** Choose an available option before opening its details on a subsequent activation. */
internal fun managedItemClickAction(item: ManagementItem): String? {
    val selection = item.selection
    return if (!item.selected && selection?.enabled == true) selection.action
        else item.primaryAction ?: selection?.takeIf { it.enabled }?.action
}

@Composable internal fun managedItemShape(position: SettingPosition, elevated: Boolean): RoundedCornerShape {
    val density = LocalDensity.current
    val outer = RoundedCornerShape(20.dp)
    val top = elevated || position == SettingPosition.SINGLE || position == SettingPosition.FIRST
    val bottom = elevated || position == SettingPosition.SINGLE || position == SettingPosition.LAST
    @Composable fun radius(outerPx: Float, outside: Boolean) = animateDpAsState(
        if (outside) with(density) { outerPx.toDp() } else 4.dp, tween(180), label = "managed-corner").value
    return RoundedCornerShape(radius(outer.topStart.toPx(Size.Zero, density), top), radius(outer.topEnd.toPx(Size.Zero, density), top),
        radius(outer.bottomEnd.toPx(Size.Zero, density), bottom), radius(outer.bottomStart.toPx(Size.Zero, density), bottom))
}

@Composable internal fun ManagedItemHeading(item: ManagementItem, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.badges.forEach { badge -> Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(badge, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
            } }
            if (item.pinned) Icon(Icons.Default.PushPin, uiText("accessibility.pinned"), Modifier.size(16.dp))
        }
        if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable internal fun ManagedItemControl(item: ManagementItem, enabled: Boolean, invoke: (String, Map<String, String>) -> Unit) {
    val toggle = item.toggle
    if (toggle != null) Switch(toggle.checked,
        onCheckedChange = feedbackChange { invoke(toggle.action, mapOf("checked" to it.toString())) }, enabled = enabled,
        colors = SwitchDefaults.colors().keepColorsWhenLocked(), modifier = Modifier.testTag("managed-toggle/" + item.id))
    else if (item.selection != null || item.selected) {
        val action = managedItemClickAction(item)
        val selectLabel = uiText("accessibility.select_item", "title" to item.title)
        RadioButton(item.selected, onClick = action?.let { feedbackClick { invoke(it, emptyMap()) } },
            enabled = enabled && item.selection?.enabled != false && action != null,
            modifier = Modifier.testTag("managed-select/" + item.id).semantics { contentDescription = selectLabel })
    }
}
