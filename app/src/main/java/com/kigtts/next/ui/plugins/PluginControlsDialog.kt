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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.themeFill
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.SettingItem
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.next.ui.settings.ToggleSetting
import com.kigtts.next.ui.settings.animatedSettingShape
import com.kigtts.plugin.api.*
import java.util.Locale

/** Plugins describe values; actual controls, picker and scrolling follow the application's theme. */
@Composable
internal fun PluginControlsDialog(request: PluginOutcome.Controls, onChange: (String, String?) -> Unit,
    onDismiss: () -> Unit, revision: Int = 0, commitSlidersOnRelease: Boolean = false) {
    var colorPicker by remember(request, revision) { mutableStateOf<PluginControl.Color?>(null) }
    val colors = remember(request, revision) { mutableStateMapOf<String, Long>() }
    MotionAlertDialog(onDismissRequest = onDismiss, title = { Text(request.title) }, text = {
        val controls = request.controls.take(100)
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            .testTag("plugin-controls"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            controls.forEachIndexed { index, control -> key(request, revision, control.actionId) {
                val position = when {
                    controls.size == 1 -> SettingPosition.SINGLE
                    index == 0 -> SettingPosition.FIRST
                    index == controls.lastIndex -> SettingPosition.LAST
                    else -> SettingPosition.MIDDLE
                }
                when (control) {
                    is PluginControl.Slider -> ControlCard(position) {
                        PluginSliderSetting(control, continuous = !commitSlidersOnRelease) { onChange(control.actionId, it) }
                    }
                    is PluginControl.Toggle -> {
                        var checked by remember { mutableStateOf(control.checked) }
                        ToggleSetting(control.title, "", null, checked, { value ->
                            checked = value
                            onChange(control.actionId, value.toString())
                        }, position = position)
                    }
                    is PluginControl.Choice -> ControlCard(position) {
                        ChoiceSetting(control) { onChange(control.actionId, it) }
                    }
                    is PluginControl.Color -> {
                        val color = colors[control.actionId] ?: control.value
                        SettingItem(control.title, "#%06X".format(Locale.ROOT, color and 0xFFFFFF), null, position,
                            Modifier.testTag("plugin-color-${control.actionId}"),
                            onClick = { colorPicker = control.copy(value = color) },
                            leading = { Box(Modifier.size(24.dp).background(Color(color), CircleShape)) })
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

@Composable
private fun ControlCard(position: SettingPosition, content: @Composable () -> Unit) {
    val shape = animatedSettingShape(position, MaterialTheme.shapes.small)
    Box(Modifier.fillMaxWidth().clip(shape).themeFill { it.surfaceContainerLow }.padding(16.dp)) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSetting(control: PluginControl.Choice, change: (String) -> Unit) {
    var selected by remember { mutableStateOf(control.selected) }
    var expanded by remember { mutableStateOf(false) }
    val options = control.options.take(100)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(control.title, style = MaterialTheme.typography.titleMedium)
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
