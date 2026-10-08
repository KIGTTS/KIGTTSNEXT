package com.kigtts.next.ui.sketch

import com.kigtts.next.ui.components.feedbackClick

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun SketchColorPicker(initial: Long, select: (Long) -> Unit, close: () -> Unit) {
    val initialHsv = remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
    val hexState = rememberTextFieldState(initialText = String.format(Locale.ROOT, "%06X", initial and 0xFFFFFF))
    var validHex by remember { mutableStateOf(true) }
    fun updateHex() {
        val value = String.format(Locale.ROOT, "%06X", AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF)
        hexState.edit { replace(0, length, value) }
    }
    LaunchedEffect(hexState) {
        snapshotFlow { hexState.text.toString() }.collect { value ->
            validHex = value.matches(Regex("[0-9A-F]{6}"))
            if (validHex) {
                val hsv = FloatArray(3)
                AndroidColor.colorToHSV((0xFF000000L or value.toLong(16)).toInt(), hsv)
                hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
            }
        }
    }
    val color = (AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)).toLong() and 0xFFFFFFFF)
    AlertDialog(onDismissRequest = close, title = { Text("取色器") }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(44.dp).background(Color(color), MaterialTheme.shapes.medium))
            Spacer(Modifier.height(12.dp))
            Text("HEX 颜色", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(state = hexState, lineLimits = TextFieldLineLimits.SingleLine, isError = !validHex,
                inputTransformation = InputTransformation {
                    val value = asCharSequence().toString().removePrefix("#").uppercase(Locale.ROOT)
                    if (value.length > 6 || value.any { it !in '0'..'9' && it !in 'A'..'F' }) revertAllChanges()
                    else replace(0, length, value)
                },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "HEX 颜色" })
            Text("色相", style = MaterialTheme.typography.labelLarge)
            Slider(hue, { hue = it; updateHex() }, valueRange = 0f..360f,
                modifier = Modifier.semantics { contentDescription = "画笔色相" })
            Text("饱和度", style = MaterialTheme.typography.labelLarge)
            Slider(saturation, { saturation = it; updateHex() },
                modifier = Modifier.semantics { contentDescription = "画笔饱和度" })
            Text("明度", style = MaterialTheme.typography.labelLarge)
            Slider(brightness, { brightness = it; updateHex() },
                modifier = Modifier.semantics { contentDescription = "画笔明度" })
        }
    }, confirmButton = { TextButton(onClick = feedbackClick { select(color) }, enabled = validHex) { Text("应用颜色") } },
        dismissButton = { TextButton(onClick = feedbackClick(close)) { Text("取消") } })
}
