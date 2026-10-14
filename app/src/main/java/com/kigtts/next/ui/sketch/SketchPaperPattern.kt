package com.kigtts.next.ui.sketch

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.floor

enum class SketchPaperPattern(val label: String) { PLAIN("纯色"), GRID("方格本"), DOTS("点阵") }

/** Pattern coordinates belong to the document. Only visible cells are drawn; zooming out coarsens the grid. */
internal fun drawSketchPaper(canvas: Canvas, visible: Rect, background: Int, pattern: SketchPaperPattern,
    referenceShorter: Float, scale: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
    canvas.drawRect(visible.left, visible.top, visible.right, visible.bottom, paint)
    if (pattern == SketchPaperPattern.PLAIN) return
    var step = (referenceShorter / 20f).coerceAtLeast(1f)
    val pixelsPerUnit = scale.coerceAtLeast(.0000001f)
    while (step * pixelsPerUnit < 12f) step *= 2f
    val brightness = Color.red(background) * .2126f + Color.green(background) * .7152f + Color.blue(background) * .0722f
    paint.color = if (brightness > 128f) Color.argb(42, 0, 0, 0) else Color.argb(55, 255, 255, 255)
    paint.strokeWidth = maxOf(referenceShorter / 1400f, .75f / pixelsPerUnit)
    val firstX = floor(visible.left / step).toInt(); val lastX = ceil(visible.right / step).toInt()
    val firstY = floor(visible.top / step).toInt(); val lastY = ceil(visible.bottom / step).toInt()
    if (pattern == SketchPaperPattern.GRID) {
        for (x in firstX..lastX) canvas.drawLine(x * step, visible.top, x * step, visible.bottom, paint)
        for (y in firstY..lastY) canvas.drawLine(visible.left, y * step, visible.right, y * step, paint)
    } else {
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = maxOf(referenceShorter / 280f, 2f / pixelsPerUnit)
        val points = FloatArray((lastX - firstX + 1) * 2)
        for (y in firstY..lastY) {
            for (x in firstX..lastX) { val index = (x - firstX) * 2; points[index] = x * step; points[index + 1] = y * step }
            canvas.drawPoints(points, paint)
        }
    }
}
