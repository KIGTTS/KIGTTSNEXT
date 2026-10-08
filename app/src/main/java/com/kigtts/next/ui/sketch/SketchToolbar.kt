@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.sketch

import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.feedbackChange

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

private enum class SketchMenu { PEN, ERASER, COLORS }

@Composable
fun SketchToolbar(state: SketchState, save: () -> Unit, clear: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf<SketchMenu?>(null) }
    var picker by remember { mutableStateOf(false) }
    HorizontalFloatingToolbar(expanded = true, modifier = modifier) {
        Box {
            IconToggleButton(checked = state.tool == SketchTool.PEN && !state.temporaryErasing,
                onCheckedChange = feedbackChange {
                    if (state.tool == SketchTool.PEN) menu = SketchMenu.PEN
                    else { state.tool = SketchTool.PEN; menu = null }
                }) { Icon(Icons.Default.Edit, "画笔") }
            DropdownMenu(expanded = menu == SketchMenu.PEN, onDismissRequest = { menu = null }) {
                Column(Modifier.width(264.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("画笔笔触", style = MaterialTheme.typography.titleMedium)
                    Text("大小 " + state.penSize.toInt() + " dp", style = MaterialTheme.typography.labelLarge)
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
            IconToggleButton(checked = state.tool == SketchTool.ERASER || state.temporaryErasing,
                onCheckedChange = feedbackChange {
                    if (state.tool == SketchTool.ERASER) menu = SketchMenu.ERASER
                    else { state.tool = SketchTool.ERASER; menu = null }
                }) { Icon(Icons.Default.AutoFixNormal, "橡皮擦") }
            DropdownMenu(expanded = menu == SketchMenu.ERASER, onDismissRequest = { menu = null }) {
                Column(Modifier.width(264.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("橡皮擦笔触", style = MaterialTheme.typography.titleMedium)
                    Text("大小 " + state.eraserSize.toInt() + " dp", style = MaterialTheme.typography.labelLarge)
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
        Box {
            IconButton(onClick = feedbackClick { menu = SketchMenu.COLORS }) { Icon(Icons.Default.Palette, "画笔颜色") }
            if (!picker) DropdownMenu(expanded = menu == SketchMenu.COLORS, onDismissRequest = { menu = null }) {
                SketchPalette(state.color, { color -> state.color = color }, { menu = null; picker = true })
            }
        }
        IconButton(onClick = feedbackClick(save)) { Icon(Icons.Default.Save, "保存画板") }
        IconButton(onClick = feedbackClick(clear)) { Icon(Icons.Default.DeleteOutline, "清空画板") }
    }
    if (picker) SketchColorPicker(state.color, { state.color = it; picker = false }, { picker = false })
}
