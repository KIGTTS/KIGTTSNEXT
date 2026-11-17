package com.kigtts.next.ui.plugins

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.kigtts.next.i18n.uiText
import com.kigtts.next.manager.ExperimentalTool
import com.kigtts.next.state.AppViewModel

@Composable internal fun BuiltInToolPermissionEntry(model: AppViewModel, id: String) {
    val tool = ExperimentalTool.entries.firstOrNull { it.id == id } ?: return
    var open by remember(id) { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text(uiText("experiments.permissions", "tool" to uiText("experiments.${tool.key}.title")))
    }
    if (open) PluginPermissionSettings(model, id, null) { open = false }
}
