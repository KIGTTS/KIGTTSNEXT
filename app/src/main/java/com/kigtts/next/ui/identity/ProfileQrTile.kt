package com.kigtts.next.ui.identity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

@Composable
internal fun ProfileQrTile(value: String, style: ProfileQrStyle, label: String, modifier: Modifier = Modifier,
    keepLight: Boolean = false) {
    val matrix by key(value) {
        produceState<com.google.zxing.common.BitMatrix?>(null) {
            this.value = withContext(Dispatchers.Default) { profileQrMatrix(value) }
        }
    }
    val palette = nativeProfileQrPalette(style, keepLight)
    val foreground = Color(palette.ink)
    val paper = Color(palette.paper)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val code = matrix
        if (code != null) {
            val qrCanvas: @Composable (Modifier) -> Unit = { placement ->
                Canvas(placement.semantics { contentDescription = if (label.isBlank()) "二维码" else "$label 二维码" }) {
            val step = floor(size.minDimension / code.width)
            if (step < 1f) return@Canvas
            val side = step * code.width
            val origin = Offset(floor((size.width - side) / 2f), floor((size.height - side) / 2f))
            drawRect(paper, origin, Size(side, side))
            for (y in 0 until code.height) for (x in 0 until code.width) if (code[x, y]) {
                val start = origin + Offset(x * step, y * step)
                if (style == ProfileQrStyle.DOTS && step >= 3f && code.qrDotAllowed(x, y))
                    drawCircle(foreground, step * .48f, start + Offset(step / 2, step / 2))
                else drawRect(foreground, start, Size(step, step))
            }
                }
            }
            val edge = minOf(maxWidth, maxHeight)
            val title: @Composable (Modifier, TextAlign) -> Unit = { position, alignment ->
                Text(label, position, style = MaterialTheme.typography.titleSmall, color = foreground,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = alignment)
            }
            when {
                label.isNotBlank() && maxWidth > maxHeight + 12.dp ->
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        qrCanvas(Modifier.size(edge))
                        title(Modifier.weight(1f).padding(horizontal = 8.dp), TextAlign.Start)
                    }
                label.isNotBlank() && maxHeight > maxWidth + 12.dp ->
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        qrCanvas(Modifier.size(edge))
                        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            title(Modifier.fillMaxWidth(), TextAlign.Center)
                        }
                    }
                else -> qrCanvas(Modifier.fillMaxSize())
            }
        } else if (value.isBlank()) Text("二维码", style = MaterialTheme.typography.labelMedium, color = foreground)
    }
}

@Composable internal fun nativeProfileQrPalette(style: ProfileQrStyle, keepLight: Boolean): ProfileQrPalette {
    val colors = MaterialTheme.colorScheme
    return profileQrPalette(style, keepLight, colors.surface.luminance() < .45f,
        colors.primary.toArgb(), colors.surfaceContainerHigh.toArgb())
}
