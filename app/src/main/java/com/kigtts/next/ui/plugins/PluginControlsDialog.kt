package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import java.util.Locale

/** Plugins describe values; actual controls, picker and scrolling follow the application's theme. */
@Composable
internal fun PluginControlsDialog(request: PluginOutcome.Controls, onChange: (String, String?) -> Unit,
    onDismiss: () -> Unit, revision: Int = 0) {
    var colorPicker by remember(request, revision) { mutableStateOf<PluginControl.Color?>(null) }
    val colors = remember(request, revision) { mutableStateMapOf<String, Long>() }
    MotionAlertDialog(onDismissRequest = onDismiss, title = { Text(request.title) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())
            .testTag("plugin-controls"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            request.controls.take(100).forEach { control -> key(request, revision, control.actionId) {
                when (control) {
                    is PluginControl.Slider -> PluginSliderSetting(control) { onChange(control.actionId, it) }
                    is PluginControl.Toggle -> {
                        var checked by remember { mutableStateOf(control.checked) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(control.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked, feedbackChange { checked = it; onChange(control.actionId, it.toString()) },
                                Modifier.semantics { contentDescription = control.title })
                        }
                    }
                    is PluginControl.Choice -> ChoiceSetting(control) { onChange(control.actionId, it) }
                    is PluginControl.Color -> {
                        val color = colors[control.actionId] ?: control.value
                        FilledTonalButton(onClick = feedbackClick { colorPicker = control.copy(value = color) },
                            modifier = Modifier.fillMaxWidth().testTag("plugin-color-${control.actionId}")) {
                            Box(Modifier.size(24.dp).background(Color(color), CircleShape))
                            Spacer(Modifier.width(12.dp)); Text(control.title, Modifier.weight(1f))
                            Text("#%06X".format(Locale.ROOT, color and 0xFFFFFF), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } }
        }
    }, confirmButton = { MotionTextButton(dialogFeedbackClick(onDismiss)) { Text(uiText("action.done")) } },
        dismissButton = { request.resetActionId?.let { reset ->
            MotionTextButton(feedbackClick { onChange(reset, null) }) { Text(uiText("camera.reset_default")) }
        } })
    colorPicker?.let { control -> key(control.actionId) {
        ColorPickerDialog(control.value, { value ->
            colors[control.actionId] = value
            colorPicker = null
            onChange(control.actionId, value.toString())
        }, { colorPicker = null }, control.title)
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSetting(control: PluginControl.Choice, change: (String) -> Unit) {
    var selected by remember { mutableStateOf(control.selected) }
    var expanded by remember { mutableStateOf(false) }
    val options = control.options.take(100)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(control.title, style = MaterialTheme.typography.bodyLarge)
        if (control.style == PluginChoiceStyle.SEGMENTED && options.size in 2..4) {
            ChoiceButtonGroup(control.title, options.map { it.id }, selected,
                { id -> options.first { it.id == id }.label }, { selected = it; change(it) }, compactLabels = true)
        } else {
            Box {
                OutlinedButton(onClick = feedbackClick { expanded = true }, modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = control.title }) {
                    Text(options.firstOrNull { it.id == selected }?.label ?: uiText("action.select"), Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded, { expanded = false }) {
                    options.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = feedbackClick {
                        selected = option.id; expanded = false; change(option.id)
                    }) }
                }
            }
        }
    }
}
