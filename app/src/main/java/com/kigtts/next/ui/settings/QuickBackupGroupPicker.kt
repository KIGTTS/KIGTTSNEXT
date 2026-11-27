package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.CategoryIcon
import com.kigtts.next.theme.ThemeIcon
import com.kigtts.plugin.api.QuickGroup
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.home.vector

@Composable
internal fun QuickBackupGroupPicker(title: String, groups: List<QuickGroup>, confirmLabel: String,
    initialSelection: Set<String> = groups.map { it.id }.toSet(), dismiss: () -> Unit, confirm: (Set<String>) -> Unit) {
    var selection by rememberSaveable(groups) { mutableStateOf(ArrayList(initialSelection)) }
    val selected = selection.toSet()
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column {
            TextButton(onClick = feedbackClick { selection = if (selected.size == groups.size) arrayListOf() else ArrayList(groups.map { it.id }) }) {
                Text(if (selected.size == groups.size) uiText("album.deselect_all") else uiText("album.select_all"))
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(groups, key = { it.id }) { group ->
                    val icon = remember(group.icon) { runCatching { CategoryIcon.valueOf(group.icon) }.getOrDefault(CategoryIcon.LIST) }
                    Row(Modifier.fillMaxWidth().testTag("backup-group/${group.id}")
                        .toggleable(group.id in selected, role = Role.Checkbox, onValueChange = { checked ->
                            selection = ArrayList(if (checked) selected + group.id else selected - group.id)
                        }).heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeIcon(icon.vector(), null, Modifier.size(24.dp).testTag("backup-group-icon/${group.id}"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(group.name, Modifier.weight(1f))
                        Checkbox(group.id in selected, null)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = feedbackClick { confirm(selected) }, enabled = selected.isNotEmpty(),
        modifier = Modifier.testTag("backup-group-confirm")) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(uiText("action.cancel")) } })
}
