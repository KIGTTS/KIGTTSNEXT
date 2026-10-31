package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Only a few dozen vector ticks are drawn; no bitmap is rebuilt as the camera reports zoom. */
@Composable
internal fun CameraZoomDial(value: Float, range: ClosedFloatingPointRange<Float>, vertical: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White)
    val valueStyle = MaterialTheme.typography.headlineSmall.copy(color = Color.White,
        shadow = Shadow(Color.Black.copy(alpha = .8f), Offset(0f, 1f), blurRadius = 5f))
    val accent = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier) {
        val radius = if (vertical) minOf(size.height * .92f, size.width * .56f) else minOf(size.width * .8f, size.height * .48f)
        val center = if (vertical) Offset(size.width / 2f, size.height + radius * .35f)
            else Offset(size.width + radius * .32f, size.height / 2f)
        val origin = if (vertical) 270f else 180f
        val progress = cameraZoomPosition(value, range)
        fun point(angle: Float, r: Float): Offset {
            val radians = Math.toRadians(angle.toDouble())
            return center + Offset(cos(radians).toFloat(), sin(radians).toFloat()) * r
        }
        drawCircle(Color.Black.copy(alpha = .64f), radius, center)
        drawCircle(Color.White.copy(alpha = .12f), radius - 1.dp.toPx(), center, style = Stroke(1.dp.toPx()))
        for (tick in 0..60) {
            val angle = origin + (tick / 60f - progress) * 145f
            val major = tick % 5 == 0
            drawLine(Color.White.copy(alpha = if (major) .9f else .48f), point(angle, radius - 8.dp.toPx()),
                point(angle, radius - (if (major) 23.dp else 16.dp).toPx()), (if (major) 1.5.dp else 1.dp).toPx())
        }
        cameraZoomStops(range).forEach { stop ->
            val angle = origin + (cameraZoomPosition(stop, range) - progress) * 145f
            val text = measurer.measure(zoomLabel(stop), labelStyle)
            val at = point(angle, radius - 42.dp.toPx())
            drawText(text, topLeft = at - Offset(text.size.width / 2f, text.size.height / 2f))
        }
        drawLine(accent, point(origin, radius - 4.dp.toPx()), point(origin, radius - 28.dp.toPx()), 3.dp.toPx())
        val text = measurer.measure(zoomLabel(value), valueStyle)
        val at = point(origin, radius + 28.dp.toPx())
        drawText(text, topLeft = at - Offset(text.size.width / 2f, text.size.height / 2f))
    }
}
