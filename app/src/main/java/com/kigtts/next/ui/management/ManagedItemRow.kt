@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.management

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.plugin.api.*
import java.io.File

@Composable
internal fun ManagedItemRow(item: ManagementItem, modifier: Modifier = Modifier, dragGrip: Modifier, reorderable: Boolean, elevated: Boolean,
    enabled: Boolean, resolve: (PluginResource) -> File?, invoke: (String, Map<String, String>) -> Unit,
    moveAccessible: (Int) -> Unit, shadowElevation: Dp = if (elevated) 8.dp else 0.dp,
    catalog: Boolean = false, trailingAction: ManagementProgressAction? = null,
    position: SettingPosition = SettingPosition.SINGLE, compactActions: Boolean = false) {
    val primary = item.primaryAction
    // Start in the current state so the first pointer frame cannot flash the old icon/shape.
    var lifted by remember(item.id) { mutableStateOf(elevated) }
    LaunchedEffect(elevated) { lifted = elevated }
    val color by animateColorAsState(if (item.selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        tween(180), label = "managed-selection-color")
    Surface(modifier.testTag("managed-item/" + item.id), shape = managedItemShape(position, lifted),
        color = color, shadowElevation = shadowElevation) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).then(if (primary != null && !elevated)
                Modifier.clickable(enabled = enabled, onClick = feedbackClick { invoke(primary, emptyMap()) }) else Modifier)
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.image != null || item.icon != PluginIcon.NONE || item.symbol != null)
                    ResourceAvatar(item, resolve, if (catalog) 64.dp else 40.dp)
                ManagedItemHeading(item, Modifier.weight(1f))
                trailingAction?.let { InlineProgressButton(item.id, it, enabled, invoke) }
                if (!compactActions) ManagedItemControl(item, enabled, invoke)
                if (reorderable) Box(dragGrip.size(40.dp).testTag("managed-grip/" + item.id).semantics {
                    contentDescription = "拖动排序"
                    customActions = listOf(CustomAccessibilityAction("上移") { moveAccessible(-1); true },
                        CustomAccessibilityAction("下移") { moveAccessible(1); true })
                }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null, Modifier.size(24.dp)) }
            }
            if (compactActions) Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ManagedItemControl(item, enabled && !elevated, invoke)
                Spacer(Modifier.weight(1f))
                CompactManagedActions(item, enabled && !elevated, invoke)
            } else if (item.actions.isNotEmpty()) ManagedItemActions(item, enabled, invoke)
        }
    }
}
