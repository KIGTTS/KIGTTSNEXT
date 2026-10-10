package com.kigtts.next.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.util.Locale

val ColorPickerPresets=listOf(0xFF00696E,0xFF6750A4,0xFF386A20,0xFF984061,0xFFFFFFFF,0xFF171D1D,
    0xFFFF6B6B,0xFFFFB86C,0xFFE8D77A,0xFF72C9AB,0xFF80BFFF,0xFFF0A8D7)

/** Reusable opaque HSV picker. Editing is local until the caller confirms the result. */
@Composable
fun ColorPickerDialog(initial:Long,onConfirm:(Long)->Unit,onDismiss:()->Unit,title:String="取色器",
    presets:List<Long> = ColorPickerPresets) {
    val initialHsv=remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(initial.toInt(),it) } }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
    val initialHex=String.format(Locale.ROOT,"%06X",initial and 0xFFFFFF)
    val hexState=rememberTextFieldState(initialText=initialHex)
    var generatedHex by remember { mutableStateOf(initialHex) }
    var validHex by remember { mutableStateOf(true) }
    fun updateHex() {
        generatedHex=String.format(Locale.ROOT,"%06X",AndroidColor.HSVToColor(floatArrayOf(hue,saturation,brightness)) and 0xFFFFFF)
        hexState.edit { replace(0,length,generatedHex) };validHex=true
    }
    fun choosePreset(color:Long) {
        val hsv=FloatArray(3);AndroidColor.colorToHSV(color.toInt(),hsv)
        if(hsv[1]>0f)hue=hsv[0]
        saturation=hsv[1];brightness=hsv[2];updateHex()
    }
    LaunchedEffect(hexState) {
        snapshotFlow { hexState.text.toString() }.collect { value ->
            validHex=value.matches(Regex("[0-9A-F]{6}"))
            // Generated HEX must not quantize HSV or wrap a 360-degree hue to zero.
            if(validHex&&value!=generatedHex) {
                val hsv=FloatArray(3);AndroidColor.colorToHSV((0xFF000000L or value.toLong(16)).toInt(),hsv)
                if(hsv[1]>0f)hue=hsv[0]
                saturation=hsv[1];brightness=hsv[2];generatedHex=value
            }
        }
    }
    val color=AndroidColor.HSVToColor(floatArrayOf(hue,saturation,brightness)).toLong() and 0xFFFFFFFF
    MotionAlertDialog(onDismissRequest=onDismiss,title={ Text(title) },text={
        Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(44.dp).dialogElementMorph().background(Color(color),MaterialTheme.shapes.medium)
                .testTag("color-preview").semantics { stateDescription="#%06X".format(Locale.ROOT,color and 0xFFFFFF) })
            Text("预设颜色",style=MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                presets.distinct().forEach { preset ->
                    IconButton(feedbackClick { choosePreset(preset) },Modifier.size(40.dp).semantics {
                        contentDescription="预设颜色 #%06X".format(Locale.ROOT,preset and 0xFFFFFF);selected=color==preset
                    }) {
                        Box(Modifier.size(28.dp).background(Color(preset),CircleShape)
                            .border(if(color==preset)3.dp else 1.dp,MaterialTheme.colorScheme.outline,CircleShape))
                    }
                }
            }
            OutlinedTextField(state=hexState,lineLimits=TextFieldLineLimits.SingleLine,isError=!validHex,label={ Text("HEX 颜色") },
                inputTransformation=InputTransformation {
                    val value=asCharSequence().toString().removePrefix("#").uppercase(Locale.ROOT)
                    if(value.length>6||value.any { it !in '0'..'9'&&it !in 'A'..'F' })revertAllChanges() else replace(0,length,value)
                },modifier=Modifier.fillMaxWidth().semantics { contentDescription="HEX 颜色" })
            ColorSlider("色相",hue,0f..360f) { hue=it;updateHex() }
            ColorSlider("饱和度",saturation) { saturation=it;updateHex() }
            ColorSlider("明度",brightness) { brightness=it;updateHex() }
        }
    },confirmButton={ TextButton(dialogFeedbackClick { onConfirm(color) },enabled=validHex) { Text("应用颜色") } },
        dismissButton={ TextButton(dialogFeedbackClick(onDismiss)) { Text("取消") } })
}

@Composable
private fun ColorSlider(label:String,value:Float,range:ClosedFloatingPointRange<Float> = 0f..1f,change:(Float)->Unit) {
    Column {
        Text(label,style=MaterialTheme.typography.labelLarge)
        Slider(value,change,valueRange=range,modifier=Modifier.semantics { contentDescription=label })
    }
}
