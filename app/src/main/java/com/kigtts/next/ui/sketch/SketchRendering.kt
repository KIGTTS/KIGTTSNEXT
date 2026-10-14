package com.kigtts.next.ui.sketch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil

/** Finite raster output is independent of the current view and the unbounded vector coordinates. */
internal fun renderSketchDocument(document: SketchDocument, maximumEdge: Int = 2400): Bitmap {
    val bounds = sketchExportBounds(document)
    require(bounds.width.isFinite() && bounds.height.isFinite() && bounds.width > 0f && bounds.height > 0f)
    val scale = minOf(1f, maximumEdge / maxOf(bounds.width, bounds.height))
    val width = ceil(bounds.width * scale).toInt().coerceIn(1, maximumEdge)
    val height = ceil(bounds.height * scale).toInt().coerceIn(1, maximumEdge)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale); canvas.translate(-bounds.left, -bounds.top)
        // Rounding up output pixels may add a fraction of a world unit at the far edges.
        val visible = Rect(bounds.left, bounds.top, bounds.left + width / scale, bounds.top + height / scale)
        drawSketchPaper(canvas, visible, document.background.toInt(), document.pattern,
            minOf(document.width, document.height).toFloat(), scale)
        val layer = canvas.saveLayer(visible.left, visible.top, visible.right, visible.bottom, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val clear = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        document.strokes.forEach { stroke ->
            paint.color = stroke.color.toInt()
            paint.alpha = (Color.alpha(stroke.color.toInt()) * stroke.alpha).toInt()
            paint.xfermode = if (stroke.erase) clear else null
            canvas.drawPath(strokePath(stroke, document.width.toFloat(), document.height.toFloat()), paint)
        }
        canvas.restoreToCount(layer)
        return bitmap
    } catch (failure: Throwable) { bitmap.recycle(); throw failure }
}
