package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kigtts.next.ui.components.vector
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.settings.*
import com.kigtts.plugin.api.*

@Composable internal fun PluginSelect(block: PageBlock.Select, enabled: Boolean, position: SettingPosition, invoke: (String, Map<String, String>) -> Unit) {
    var expanded by remember(block.title) { mutableStateOf(false) }
    Box {
        SettingItem(block.title, block.options.firstOrNull { it.id == block.selected }?.label.orEmpty(), (if(block.icon == PluginIcon.NONE && block.symbol == null) null else block.icon.vector(block.symbol)), position,
            enabled = enabled, onClick = { expanded = true }, trailing = { Icon(Icons.Default.ArrowDropDown, null) })
        DropdownMenu(expanded, { expanded = false }) {
            block.options.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = feedbackClick { expanded = false; invoke(option.id, emptyMap()) }) }
        }
    }
}
