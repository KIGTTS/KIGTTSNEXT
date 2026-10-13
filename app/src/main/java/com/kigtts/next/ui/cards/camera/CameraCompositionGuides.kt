package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun CameraCompositionGuides(grid: CameraGrid, level: Boolean, rotation: Int, active: Boolean,
    modifier: Modifier = Modifier) {
    val reading = rememberCameraLevel(rotation, active && level)
    Canvas(modifier.testTag("camera-composition-guides")) {
        fun line(start: Offset, end: Offset) {
            drawLine(Color.Black.copy(alpha = .18f), start, end, 2.dp.toPx())
            drawLine(Color.White.copy(alpha = .48f), start, end, .7.dp.toPx())
        }
        val divisions = when (grid) { CameraGrid.OFF -> 0; CameraGrid.THIRDS -> 3; CameraGrid.SQUARE -> 4 }
        for (index in 1 until divisions) {
            val fraction = index.toFloat() / divisions
            line(Offset(size.width * fraction, 0f), Offset(size.width * fraction, size.height))
            line(Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction))
        }
        if (level) reading.value?.let { value ->
            val tint = if (value.aligned) Color(0xFFFFDD66) else Color.White.copy(alpha = .85f)
            val reach = 34.dp.toPx(); val stroke = 2.dp.toPx()
            if (value.nearFlat) {
                drawCircle(Color.Black.copy(alpha = .35f), 15.dp.toPx(), center)
                drawCircle(Color.White.copy(alpha = .7f), 14.dp.toPx(), center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                val marker = center + Offset(value.flatX, -value.flatY) * reach * 2f
                drawCircle(tint, 4.dp.toPx(), marker)
                line(center - Offset(6.dp.toPx(), 0f), center + Offset(6.dp.toPx(), 0f))
                line(center - Offset(0f, 6.dp.toPx()), center + Offset(0f, 6.dp.toPx()))
            } else {
                line(center - Offset(reach + 9.dp.toPx(), 0f), center - Offset(reach - 2.dp.toPx(), 0f))
                line(center + Offset(reach - 2.dp.toPx(), 0f), center + Offset(reach + 9.dp.toPx(), 0f))
                rotate(value.rollDegrees, center) {
                    drawLine(Color.Black.copy(alpha = .4f), center - Offset(reach, 0f), center + Offset(reach, 0f), stroke + 2.dp.toPx(), StrokeCap.Round)
                    drawLine(tint, center - Offset(reach, 0f), center + Offset(reach, 0f), stroke, StrokeCap.Round)
                }
            }
        }
    }
}
