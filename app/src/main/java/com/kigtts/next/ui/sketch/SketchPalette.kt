package com.kigtts.next.ui.sketch

import com.kigtts.next.ui.components.feedbackClick

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

val SketchPresetColors = listOf(0xFF65D7DE, 0xFFFFFFFF, 0xFF171D1D, 0xFFFFB4AB, 0xFFFF6B6B,
    0xFFFFB86C, 0xFFE8D77A, 0xFF9CD67A, 0xFF72C9AB, 0xFF80BFFF, 0xFFB6A0ED, 0xFFF0A8D7)

@Composable
fun SketchPalette(current: Long, select: (Long) -> Unit, openPicker: () -> Unit) {
    Column(Modifier.width(312.dp).padding(12.dp)) {
        Text("画笔颜色", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = feedbackClick(openPicker), modifier = Modifier.size(48.dp).testTag("current-sketch-color")) {
                Box(Modifier.size(32.dp).background(Color(current), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Colorize, "当前颜色，打开取色器", Modifier.size(18.dp),
                        tint = contrastingColor(current))
                }
            }
            VerticalDivider(Modifier.padding(horizontal = 8.dp).height(36.dp))
            Row(Modifier.weight(1f).height(40.dp).horizontalScroll(rememberScrollState()).testTag("sketch-preset-colors"),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SketchPresetColors.forEach { color ->
                    IconButton(onClick = feedbackClick { select(color) }, modifier = Modifier.size(40.dp).semantics {
                        contentDescription = "预设颜色 #%06X".format(color and 0xFFFFFF)
                        selected = color == current
                    }) {
                        Box(Modifier.size(28.dp).background(Color(color), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
                            if (color == current) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = contrastingColor(color))
                        }
                    }
                }
            }
        }
    }
}

private fun contrastingColor(color: Long): Color {
    val red = ((color shr 16) and 255).toFloat()
    val green = ((color shr 8) and 255).toFloat()
    val blue = (color and 255).toFloat()
    return if (.2126f * red + .7152f * green + .0722f * blue > 145f) Color.Black else Color.White
}
