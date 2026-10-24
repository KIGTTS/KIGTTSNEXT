package com.kigtts.next.ui.plugins

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.SettingItem
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.plugin.api.PageBlock
import com.kigtts.plugin.api.StrokePoint

@Composable
internal fun PluginStrokeGuide(guide: PageBlock.StrokeGuide, enabled: Boolean,
    invoke: (String, Map<String, String>) -> Unit, position: SettingPosition) {
    var preview by remember(guide.title) { mutableStateOf(false) }
    val icon = remember(guide.points) { strokeIcon(guide.points) }
    SettingItem(guide.title, guide.summary, icon, position,
        Modifier.testTag("stroke-guide/" + guide.title), onClick = { preview = true },
        trailing = {
            guide.toggle?.let { toggle ->
                Switch(toggle.checked, feedbackChange { value ->
                    if (enabled) invoke(toggle.action, mapOf("value" to "$value", "checked" to "$value"))
                }, modifier = Modifier.testTag("stroke-toggle/" + guide.title))
            } ?: Icon(Icons.Default.PlayArrow, "查看绘制说明")
        }) {
        if (guide.actions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            guide.actions.forEach { action ->
                TextButton(onClick = feedbackClick { if (enabled) invoke(action.id, emptyMap()) }) {
                    Icon(action.icon.vector(action.symbol), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(action.label)
                }
            }
        }
    }
    if (preview) MotionAlertDialog(onDismissRequest = { preview = false },
        title = { Text(guide.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StrokeReplay(guide.points, Modifier.fillMaxWidth().height(200.dp).testTag("stroke-replay"))
                Text(guide.instruction, style = MaterialTheme.typography.bodyLarge)
                Text("从圆点起笔，一笔画完", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton(onClick = dialogFeedbackClick { preview = false }) { Text("知道了") } })
}

/** Keep paths as vectors, including the starting dot, rather than decoding raster thumbnails. */
internal fun strokeIcon(points: List<StrokePoint>): ImageVector = ImageVector.Builder(
    name = "Stroke", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(2 + p.x * 20, 2 + p.y * 20) else lineTo(2 + p.x * 20, 2 + p.y * 20) }
    }
    points.firstOrNull()?.let { p ->
        path(fill = SolidColor(Color.Black)) {
            val x = 2 + p.x * 20; val y = 2 + p.y * 20; val r = 1.7f
            moveTo(x + r, y); arcTo(r, r, 0f, true, true, x - r, y)
            arcTo(r, r, 0f, true, true, x + r, y); close()
        }
    }
}.build()

@Composable
private fun StrokeReplay(points: List<StrokePoint>, modifier: Modifier) {
    val replay = rememberInfiniteTransition(label = "stroke-replay")
    val elapsed by replay.animateFloat(0f, 2800f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "stroke-time")
    val ink = MaterialTheme.colorScheme.primary
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.large) {
        Canvas(Modifier.fillMaxSize().padding(24.dp)) {
            val side = minOf(size.width, size.height)
            val origin = Offset((size.width - side) / 2, (size.height - side) / 2)
            fun location(p: StrokePoint) = origin + Offset(p.x * side, p.y * side)
            val full = Path().apply { points.forEachIndexed { i, p ->
                val at = location(p); if (i == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
            } }
            val measure = PathMeasure().apply { setPath(full, false) }
            val current = Path(); measure.getSegment(0f, measure.length * ((elapsed - 200f) / 1500f).coerceIn(0f, 1f), current)
            val style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(full, ink.copy(alpha = .12f), style = style)
            drawPath(current, ink.copy(alpha = (1f - (elapsed - 2350f) / 220f).coerceIn(0f, 1f)), style = style)
            points.firstOrNull()?.let { drawCircle(ink, 5.dp.toPx(), location(it)) }
        }
    }
}
