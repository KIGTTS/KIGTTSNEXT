package com.kigtts.next.ui.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*

/** One primary settings target; secondary management operations belong to the overflow. */
@Composable internal fun CompactManagedActions(item: ManagementItem, enabled: Boolean, invoke: (String, Map<String, String>) -> Unit) {
    val primary = item.actions.firstOrNull { it.id == item.primaryAction }
    val secondary = item.actions.filterNot { it.id == primary?.id }
    var menu by remember(item.id) { mutableStateOf(false) }
    BackHandler(menu) { menu = false }
    primary?.let { action -> ManagedActionTooltip(action.label) {
        MotionIconButton(onClick = feedbackClick { invoke(action.id, emptyMap()) }, enabled = enabled,
            modifier = Modifier.size(40.dp).testTag("managed-action/${item.id}/${action.id}")) {
            Icon(action.icon.vector(action.symbol), action.label, Modifier.size(24.dp))
        }
    } }
    if (secondary.isNotEmpty()) Box {
        MotionIconButton(onClick = feedbackClick { menu = true }, enabled = enabled,
            modifier = Modifier.size(40.dp).testTag("managed-more/${item.id}")) { Icon(Icons.Default.MoreHoriz, "更多操作") }
        DropdownMenu(menu && enabled, { menu = false }) {
            secondary.forEach { action -> DropdownMenuItem(text = { Text(action.label) },
                leadingIcon = { Icon(action.icon.vector(action.symbol), null) },
                onClick = feedbackClick { menu = false; invoke(action.id, emptyMap()) }) }
        }
    }
}
