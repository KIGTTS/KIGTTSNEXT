package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText
import com.kigtts.next.ui.components.feedbackClick

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.plugin.api.*

@Composable internal fun ChatModelControl(control: ChatControl, enabled: Boolean, invoke: (String, Map<String, String>) -> Unit,
    reasoning: ChatControl? = null, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val selected = control.options.firstOrNull { it.action == control.selected }
    AssistChip(onClick = feedbackClick { open = true }, enabled = enabled,
        label = { Text(selected?.label ?: uiText("chat.model"), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)) },
        modifier = modifier.testTag("plugin-chat-control/MODEL"))
    if (open) ModelDialog(control, reasoning, enabled, invoke, { open = false }) { action ->
        if (control.options.firstOrNull { it.action == action }?.section.isNullOrBlank()) open = false
        invoke(action, emptyMap())
    }
}
@Composable private fun ModelDialog(control: ChatControl, reasoning: ChatControl?, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit, dismiss: () -> Unit, choose: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    MotionAlertDialog(onDismissRequest = dismiss, title = { Text(uiText("chat.models")) }, text = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(search, { search = it }, singleLine = true, placeholder = { Text(uiText("chat.model_search")) },
            leadingIcon = { Icon(Icons.Default.Search, null) }, shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 80.dp, max = 400.dp).testTag("chat-model-list")) {
                val filtered = control.options.filter { it.section.isNotBlank() && (it.label.contains(search, true) || it.section.contains(search, true)) }
                filtered.groupBy { it.section }.forEach { (provider, models) ->
                    item("heading/$provider") { Text(provider, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    itemsIndexed(models, key = { _, option -> option.action }) { index, option ->
                        ModelOptionRow(option, option.action == control.selected, index, models.size, enabled) { choose(option.action) }
                    }
                }
            control.options.filter { it.section.isBlank() }.forEach { option -> item(option.action) { TextButton(onClick = feedbackClick { choose(option.action) }) { Text(option.label) } } }
        }
        reasoning?.takeIf { it.options.size >= 2 }?.let {
            HorizontalDivider()
            ChatReasoningSlider(it, enabled, invoke)
        }
    } }, confirmButton = { TextButton(onClick = feedbackClick(dismiss)) { Text(uiText("action.close")) } })
}
@Composable private fun ModelOptionRow(option: ChatOption, selected: Boolean, index: Int, count: Int,
    enabled: Boolean, choose: () -> Unit) {
    val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, label = "model-selection")
    val shape = if (selected) RoundedCornerShape(24.dp) else RoundedCornerShape(
        topStart = if (index == 0) 20.dp else 4.dp, topEnd = if (index == 0) 20.dp else 4.dp,
        bottomEnd = if (index == count - 1) 20.dp else 4.dp, bottomStart = if (index == count - 1) 20.dp else 4.dp)
    Surface(Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("chat-model-option/" + option.action), color = color, shape = shape) {
        Row(Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = feedbackClick(choose))
            .heightIn(min = 60.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(option.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
            RadioButton(selected, onClick = null, enabled = enabled)
        }
    }
}
@Composable internal fun ChatReasoningSlider(control: ChatControl, enabled: Boolean, invoke: (String, Map<String, String>) -> Unit) {
    val selected = control.options.indexOfFirst { it.action == control.selected }.coerceAtLeast(0)
    var position by remember(control.selected, control.options) { mutableFloatStateOf(selected.toFloat()) }
    val index = kotlin.math.round(position).toInt().coerceIn(control.options.indices)
    Column(Modifier.fillMaxWidth().testTag("chat-reasoning-slider")) {
        Row { Text(control.title, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.weight(1f)); Text(control.options[index].label, style = MaterialTheme.typography.labelMedium) }
        Slider(position, { position = it }, enabled = enabled, valueRange = 0f..control.options.lastIndex.toFloat(), steps = (control.options.size - 2).coerceAtLeast(0),
            onValueChangeFinished = { if (index != selected) invoke(control.options[index].action, emptyMap()) })
    }
}
