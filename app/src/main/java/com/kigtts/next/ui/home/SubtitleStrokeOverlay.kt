package com.kigtts.next.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel

/** Render a stroke while keeping taps and multi-touch gestures available to the home screen. */
@Composable
internal fun SubtitleStrokeOverlay(model: AppViewModel, modifier: Modifier = Modifier) {
    var trail by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val color = MaterialTheme.colorScheme.primary.copy(alpha = .65f)
    Canvas(modifier.testTag("subtitle-renderer-input").subtitleInputGestures(model)
        .pointerInput(model) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val points = mutableListOf(down.position)
                var cancelled = false
                var drawing = false
                var endTime = down.uptimeMillis
                do {
                    val event = awaitPointerEvent()
                    val point = event.changes.firstOrNull { it.id == down.id }
                    if (event.changes.count { it.pressed } > 1 || point == null || point.isConsumed) cancelled = true
                    if (!cancelled && point != null) {
                        endTime = point.uptimeMillis
                        if (points.last().getDistanceTo(point.position) >= 2.dp.toPx() && points.size < 512) points += point.position
                        val distance = (point.position - down.position).getDistance()
                        if (distance > viewConfiguration.touchSlop) drawing = true
                        if (drawing) { point.consume(); trail = points.toList() }
                    }
                    if (cancelled) trail = emptyList()
                } while (event.changes.any { it.pressed })
                trail = emptyList()
                val duration = endTime - down.uptimeMillis
                if (!cancelled && drawing && points.size >= 8 && duration in 120..5000) {
                    model.submitSubtitleStroke(points, duration, size.width, size.height, 22.dp.toPx())
                }
            }
        }) {
        if (trail.size > 1) {
            val path = Path().apply { moveTo(trail[0].x, trail[0].y); trail.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun Offset.getDistanceTo(other: Offset) = (this - other).getDistance()
