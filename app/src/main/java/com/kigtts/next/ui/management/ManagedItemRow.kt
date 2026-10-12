@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.management

import com.kigtts.next.ui.components.MotionIconButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.PopupProperties
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import java.io.File
import androidx.compose.animation.*
import androidx.compose.animation.core.tween

@Composable
internal fun ManagedItemRow(item: ManagementItem, modifier: Modifier = Modifier, dragGrip: Modifier, reorderable: Boolean, elevated: Boolean,
    enabled: Boolean, resolve: (PluginResource) -> File?, invoke: (String, Map<String, String>) -> Unit,
    moveAccessible: (Int) -> Unit, shadowElevation: Dp = if (elevated) 8.dp else 0.dp) {
    var menu by remember(item.id) { mutableStateOf(false) }
    val primary = item.primaryAction
    val color by animateColorAsState(if (item.selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        tween(180), label = "managed-selection-color")
    BackHandler(menu) { menu = false }
    Surface(modifier.testTag("managed-item/" + item.id), shape = MaterialTheme.shapes.medium,
        color = color,
        shadowElevation = shadowElevation) {
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).then(if (primary != null && !elevated)
            Modifier.clickable(enabled = enabled, onClick = feedbackClick { invoke(primary, emptyMap()) }) else Modifier)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ResourceAvatar(item, resolve)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    AnimatedVisibility(item.pinned, enter=fadeIn(tween(160))+expandHorizontally(tween(180)),exit=fadeOut(tween(120))+shrinkHorizontally(tween(180))) {
                        Icon(Icons.Default.PushPin, "已置顶", Modifier.size(16.dp))
                    }
                    AnimatedVisibility(item.selected, enter=fadeIn(tween(160))+expandHorizontally(tween(180)),exit=fadeOut(tween(120))+shrinkHorizontally(tween(180))) {
                        Icon(Icons.Default.CheckCircle, "当前选用", Modifier.size(16.dp))
                    }
                }
                PageContentMotion(item.subtitle) { subtitle ->
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            item.toggle?.let { toggle ->
                Switch(toggle.checked, enabled = enabled, onCheckedChange = if (elevated) null else feedbackChange { invoke(toggle.action, mapOf("checked" to it.toString())) },
                    colors = SwitchDefaults.colors().keepColorsWhenLocked(),
                    modifier = Modifier.testTag("managed-toggle/" + item.id))
            }
            if (reorderable) Box(dragGrip.size(32.dp).testTag("managed-grip/" + item.id), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DragIndicator, "拖动排序", Modifier.size(20.dp))
            }
            if (item.actions.isNotEmpty() || reorderable) Box {
                MotionIconButton(onClick = feedbackClick { menu = true }, enabled = enabled, colors = IconButtonDefaults.iconButtonColors().keepColorsWhenLocked(), modifier = Modifier.size(32.dp).testTag("managed-more/" + item.id)) {
                    Icon(Icons.Default.MoreVert, "条目操作", Modifier.size(20.dp))
                }
                DropdownMenu(menu && enabled, { menu = false }, properties = PopupProperties(focusable = false)) {
                    item.actions.forEach { action -> DropdownMenuItem(text = { Text(action.label) }, leadingIcon = { Icon(action.icon.vector(), null) },
                        onClick = feedbackClick { menu = false; invoke(action.id, emptyMap()) }) }
                    if (reorderable) {
                        DropdownMenuItem(text = { Text("上移") }, onClick = feedbackClick { menu = false; moveAccessible(-1) })
                        DropdownMenuItem(text = { Text("下移") }, onClick = feedbackClick { menu = false; moveAccessible(1) })
                    }
                }
            }
        }
    }
}
