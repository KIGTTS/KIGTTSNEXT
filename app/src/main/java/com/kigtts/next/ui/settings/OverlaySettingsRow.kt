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
import com.kigtts.next.i18n.rememberUiCopy

/** Uses the same segmented surface and reveal rail as other management lists. */
@Composable internal fun OverlaySettingsRow(entry: OverlayShortcut, position: SettingPosition,
    modifier: Modifier, grip: Modifier, elevated: Boolean, active: Boolean, activate: () -> Unit,
    edit: () -> Unit, remove: () -> Unit, move: (Int) -> Unit, holdRequest: Int = 0) {
    val copy = rememberUiCopy()
    val content: @Composable (Boolean, () -> Unit, () -> Unit) -> Unit = { revealed, toggle, close ->
        val menu = feedbackClick(toggle)
        Surface(shape = managedItemShape(position, revealed), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().heightIn(min = 80.dp)
                .then(if (elevated) Modifier else Modifier.combinedClickable(
                    onClick = feedbackClick { if (revealed) close() }).semantics { onLongClick(copy("overlay.row.more")) { menu(); true } })
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.alpha(if (entry.enabled) 1f else .45f)) { OverlayShortcutIcon(entry) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                    if (!entry.enabled) Text(copy("overlay.row.unavailable"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MotionIconButton(onClick = feedbackClick(toggle), modifier = Modifier.size(40.dp)
                    .testTag("overlay-more/${entry.id}")) { Icon(Icons.Default.MoreHoriz, copy("overlay.row.more")) }
                Box(Modifier.size(40.dp).then(grip).semantics {
                    contentDescription = copy("overlay.row.drag", "title" to entry.title)
                    customActions = listOf(CustomAccessibilityAction(copy("overlay.row.up")) { move(-1); true },
                        CustomAccessibilityAction(copy("overlay.row.down")) { move(1); true })
                }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null, Modifier.size(22.dp)) }
            }
        }
    }
    Box(modifier) {
    if (elevated) content(false, {}, {})
    else SwipeRevealRow("overlay/${entry.id}", active, activate, modifier = Modifier.fillMaxWidth(),
        endActions = listOf(RevealAction("settings", copy("overlay.row.configure", "title" to entry.title), Icons.Default.Tune),
            RevealAction("delete", copy("overlay.row.remove", "title" to entry.title), Icons.Default.Delete, destructive = true)),
        invoke = { when (it) { "settings" -> edit(); "delete" -> remove() } }, holdRequest = holdRequest, content = content)
    }
}
