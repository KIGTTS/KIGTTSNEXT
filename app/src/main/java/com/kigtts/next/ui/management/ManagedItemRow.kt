@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.management

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Delete
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
    position: SettingPosition = SettingPosition.SINGLE, compactActions: Boolean = false,
    revealActive: Boolean = false, activateReveal: () -> Unit = {}, holdRequest: Int = 0) {
    val primary = item.primaryAction
    val clickAction = managedItemClickAction(item)
    val direct = if (compactActions) item.actions.firstOrNull { it.id == primary } else null
    val secondary = item.actions.filterNot { it.id == primary }
    val pin = secondary.filter { it.icon == PluginIcon.PIN || it.symbol == "push_pin" }
    val other = secondary.filterNot { it in pin }.sortedBy { it.isDestructive() }
    fun PageAction.reveal() = RevealAction(id, label, if (isDestructive()) Icons.Default.Delete else icon.vector(symbol), isDestructive())
    val color by animateColorAsState(if (item.selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        tween(180), label = "managed-selection-color")
    val row: @Composable (Boolean, () -> Unit, () -> Unit) -> Unit = { revealed, toggleActions, closeActions ->
        val menu = feedbackClick(toggleActions)
    Surface(Modifier.fillMaxWidth().testTag("managed-item/" + item.id), shape = managedItemShape(position, revealed),
        color = color, shadowElevation = shadowElevation) {
            Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).then(if ((clickAction != null || secondary.isNotEmpty() || revealed) && !elevated)
                Modifier.combinedClickable(enabled = enabled, onLongClick = if (!reorderable && secondary.isNotEmpty()) menu else null, onClick = feedbackClick {
                    if (revealed) closeActions() else clickAction?.let { invoke(it, emptyMap()) }
                }).then(if (reorderable && enabled && secondary.isNotEmpty()) Modifier.semantics {
                    onLongClick("更多操作") { menu(); true }
                } else Modifier) else Modifier)
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.image != null || item.icon != PluginIcon.NONE || item.symbol != null)
                    ResourceAvatar(item, resolve, if (catalog) 64.dp else 40.dp)
                ManagedItemHeading(item, Modifier.weight(1f))
                trailingAction?.let { InlineProgressButton(item.id, it, enabled, invoke) }
                ManagedItemControl(item, enabled, invoke)
                direct?.let { action -> ManagedActionTooltip(action.label) {
                    MotionIconButton(onClick = feedbackClick { invoke(action.id, emptyMap()) }, enabled = enabled,
                        modifier = Modifier.size(40.dp).testTag("managed-action/${item.id}/${action.id}")) {
                        Icon(action.icon.vector(action.symbol), action.label, Modifier.size(24.dp))
                    }
                } }
                if (secondary.isNotEmpty()) MotionIconButton(onClick = feedbackClick(toggleActions), enabled = enabled,
                    modifier = Modifier.size(40.dp).testTag("managed-more/${item.id}")) { Icon(Icons.Default.MoreHoriz, "更多操作") }
                if (reorderable) Box(dragGrip.size(40.dp).testTag("managed-grip/" + item.id).semantics {
                    contentDescription = "拖动排序"
                    customActions = listOf(CustomAccessibilityAction("上移") { moveAccessible(-1); true },
                        CustomAccessibilityAction("下移") { moveAccessible(1); true })
                }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null, Modifier.size(24.dp)) }
            }
    }
    }
    if (elevated) Box(modifier) { row(false, {}, {}) }
    else SwipeRevealRow(item.id, revealActive, activateReveal, pin.map { it.reveal() }, other.map { it.reveal() },
        enabled = enabled, modifier = modifier, fullSwipeStartAction = pin.firstOrNull()?.id, invoke = { invoke(it, emptyMap()) },
        holdRequest = holdRequest, content = row)
}

private fun PageAction.isDestructive() = symbol in setOf("delete", "delete_forever", "delete_outline") ||
    label.startsWith("删除") || label.startsWith("卸载")
