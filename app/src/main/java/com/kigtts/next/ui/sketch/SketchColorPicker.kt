package com.kigtts.next.ui.sketch

import androidx.compose.runtime.Composable
import com.kigtts.next.ui.components.ColorPickerDialog

@Composable
fun SketchColorPicker(initial:Long,select:(Long)->Unit,close:()->Unit) =
    ColorPickerDialog(initial,select,close,presets=SketchPresetColors)
