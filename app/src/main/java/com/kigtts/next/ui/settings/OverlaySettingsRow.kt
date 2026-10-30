package com.kigtts.next.ui.settings

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.overlay.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.management.managedItemShape

/** Uses the same segmented surface and reveal rail as other management lists. */
@Composable internal fun OverlaySettingsRow(entry: OverlayShortcut, position: SettingPosition,
    modifier: Modifier, grip: Modifier, elevated: Boolean, active: Boolean, activate: () -> Unit,
    edit: () -> Unit, remove: () -> Unit, move: (Int) -> Unit) {
    val content: @Composable (Boolean, () -> Unit, () -> Unit) -> Unit = { revealed, toggle, close ->
        Surface(shape = managedItemShape(position, revealed), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().heightIn(min = 80.dp)
                .then(if (elevated) Modifier else Modifier.combinedClickable(
                    onLongClick = feedbackClick(toggle), onClick = feedbackClick { if (revealed) close() }))
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.alpha(if (entry.enabled) 1f else .45f)) { OverlayShortcutIcon(entry) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                    if (!entry.enabled) Text("暂不可用", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MotionIconButton(onClick = feedbackClick(toggle), modifier = Modifier.size(40.dp)
                    .testTag("overlay-more/${entry.id}")) { Icon(Icons.Default.MoreHoriz, "更多操作") }
                Box(Modifier.size(40.dp).then(grip).semantics {
                    contentDescription = "拖动排序${entry.title}"
                    customActions = listOf(CustomAccessibilityAction("上移") { move(-1); true },
                        CustomAccessibilityAction("下移") { move(1); true })
                }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null, Modifier.size(22.dp)) }
            }
        }
    }
    Box(modifier) {
    if (elevated) content(false, {}, {})
    else SwipeRevealRow("overlay/${entry.id}", active, activate, modifier = Modifier.fillMaxWidth(),
        endActions = listOf(RevealAction("settings", "配置${entry.title}", Icons.Default.Tune),
            RevealAction("delete", "移除${entry.title}", Icons.Default.Delete, destructive = true)),
        invoke = { when (it) { "settings" -> edit(); "delete" -> remove() } }, content = content)
    }
}
