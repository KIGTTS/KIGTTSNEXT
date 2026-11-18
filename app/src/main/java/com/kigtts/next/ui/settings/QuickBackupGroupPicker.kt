package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.plugin.api.QuickGroup
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.feedbackClick

@Composable
internal fun QuickBackupGroupPicker(title: String, groups: List<QuickGroup>, confirmLabel: String,
    dismiss: () -> Unit, confirm: (Set<String>) -> Unit) {
    var selected by remember(groups) { mutableStateOf(groups.map { it.id }.toSet()) }
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column {
            TextButton(onClick = feedbackClick { selected = if (selected.size == groups.size) emptySet() else groups.map { it.id }.toSet() }) {
                Text(if (selected.size == groups.size) "取消全选" else "全选")
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(groups, key = { it.id }) { group ->
                    Row(Modifier.fillMaxWidth().clickable {
                        selected = if (group.id in selected) selected - group.id else selected + group.id
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(group.id in selected, null)
                        Spacer(Modifier.width(8.dp)); Text(group.name, Modifier.weight(1f))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = feedbackClick { confirm(selected) }, enabled = selected.isNotEmpty()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(uiText("action.cancel")) } })
}
