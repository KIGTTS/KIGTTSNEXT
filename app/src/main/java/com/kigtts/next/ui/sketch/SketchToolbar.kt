@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.sketch

import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.feedbackChange
import com.kigtts.next.ui.components.MotionIconButton
import com.kigtts.next.ui.components.MotionIconToggleButton

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

private enum class SketchMenu { PEN, ERASER }

@Composable
fun SketchToolbar(state: SketchState, save: () -> Unit, clear: () -> Unit, modifier: Modifier = Modifier,
    vertical: Boolean = false, enabled: Boolean = true) {
    var menu by remember { mutableStateOf<SketchMenu?>(null) }
    var picker by remember { mutableStateOf(false) }
    val tools: @Composable () -> Unit = {
        Box {
            MotionIconToggleButton(checked = state.tool == SketchTool.PEN && !state.temporaryErasing,
                enabled = enabled, onCheckedChange = {
                    if (state.tool == SketchTool.PEN) menu = SketchMenu.PEN
                    else { state.tool = SketchTool.PEN; menu = null }
                }) { Icon(Icons.Default.Edit, "画笔") }
            DropdownMenu(expanded = menu == SketchMenu.PEN, onDismissRequest = { menu = null }) {
                Column(Modifier.width(264.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("画笔笔触", style = MaterialTheme.typography.titleMedium)
                    Text("笔触大小", style = MaterialTheme.typography.labelLarge)
                    Slider(state.penSize, { state.penSize = it }, valueRange = 1f..36f,
                        modifier = Modifier.semantics { contentDescription = "画笔大小" })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PenType.entries.forEach { type ->
                            FilterChip(state.penType == type, feedbackClick { state.penType = type }, label = { Text(type.label) })
                        }
                    }
                }
            }
        }
        Box {
            MotionIconToggleButton(checked = state.tool == SketchTool.ERASER || state.temporaryErasing,
                enabled = enabled, onCheckedChange = {
                    if (state.tool == SketchTool.ERASER) menu = SketchMenu.ERASER
                    else { state.tool = SketchTool.ERASER; menu = null }
                }) { Icon(SketchEraserIcon, "橡皮擦") }
            DropdownMenu(expanded = menu == SketchMenu.ERASER, onDismissRequest = { menu = null }) {
                Column(Modifier.width(264.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("橡皮擦笔触", style = MaterialTheme.typography.titleMedium)
                    Text("擦除大小", style = MaterialTheme.typography.labelLarge)
                    Slider(state.eraserSize, { state.eraserSize = it }, valueRange = 8f..96f,
                        modifier = Modifier.semantics { contentDescription = "橡皮擦大小" })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EraserType.entries.forEach { type ->
                            FilterChip(state.eraserType == type, feedbackClick { state.eraserType = type }, label = { Text(type.label) })
                        }
                    }
                }
            }
        }
        MotionIconButton(onClick = feedbackClick { menu = null; picker = true }, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "画笔颜色" }) {
            Box(Modifier.size(26.dp).background(Color(state.color), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        }
        MotionIconButton(onClick = feedbackClick(save), enabled = enabled) { Icon(Icons.Default.Save, "保存画板") }
        MotionIconButton(onClick = feedbackClick(clear), enabled = enabled) { Icon(Icons.Default.DeleteOutline, "清空画板") }
    }
    if (vertical) VerticalFloatingToolbar(expanded = true, modifier = modifier) { tools() }
    else HorizontalFloatingToolbar(expanded = true, modifier = modifier) { tools() }
    if (picker) SketchColorPicker(state.color, { state.color = it; picker = false }, { picker = false })
}
