@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*

/** Reserve the overflow target before placing actions, so the last button never spills out. */
internal fun managedVisibleActions(widths: List<Float>, available: Float, overflow: Float): Int {
    if (widths.sum() <= available) return widths.size
    var used = overflow
    return widths.takeWhile { width -> (used + width <= available).also { if (it) used += width } }.size
}

@Composable internal fun ManagedItemActions(item: ManagementItem, enabled: Boolean, invoke: (String, Map<String, String>) -> Unit) {
    var menu by remember(item.id) { mutableStateOf(false) }
    BackHandler(menu) { menu = false }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp).testTag("managed-actions/" + item.id)) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val style = MaterialTheme.typography.labelLarge
        val widths = item.actions.map { action -> if (action.icon != PluginIcon.NONE || action.symbol != null) 48f else
            with(density) { measurer.measure(action.label, style).size.width.toDp().value + 24 }.coerceIn(48f, 160f) }
        val count = managedVisibleActions(widths, maxWidth.value, 48f)
        Row {
            item.actions.take(count).forEachIndexed { index, action ->
                val modifier = Modifier.width(widths[index].dp).heightIn(min = 48.dp).testTag("managed-action/${item.id}/${action.id}")
                if (action.icon == PluginIcon.NONE && action.symbol == null) MotionTextButton(
                    onClick = { invoke(action.id, emptyMap()) }, enabled = enabled, modifier = modifier) { Text(action.label, maxLines = 1) }
                else ManagedActionTooltip(action.label) {
                    MotionIconButton(onClick = { invoke(action.id, emptyMap()) }, enabled = enabled, modifier = modifier) {
                        Icon(action.icon.vector(action.symbol), action.label, Modifier.size(24.dp))
                    }
                }
            }
            if (count < item.actions.size) Box {
                ManagedActionTooltip("更多操作") {
                    MotionIconButton(onClick = { menu = true }, enabled = enabled,
                        modifier = Modifier.size(48.dp).testTag("managed-more/" + item.id)) { Icon(Icons.Default.MoreHoriz, "更多操作") }
                }
                DropdownMenu(menu && enabled, { menu = false }) {
                    item.actions.drop(count).forEach { action -> DropdownMenuItem(text = { Text(action.label) },
                        leadingIcon = if (action.icon == PluginIcon.NONE && action.symbol == null) null else ({ Icon(action.icon.vector(action.symbol), null) }),
                        onClick = feedbackClick { menu = false; invoke(action.id, emptyMap()) }) }
                }
            }
        }
    }
}

@Composable private fun ManagedActionTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface) { Text(label) } }, state = rememberTooltipState(), content = content)
}
