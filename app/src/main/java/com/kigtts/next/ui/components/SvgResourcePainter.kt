package com.kigtts.next.ui.components

import android.graphics.Picture
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter

/** A validated SVG picture, tinted at draw time so cached artwork follows live theme changes. */
internal class SvgResourcePainter(private val picture: Picture) : Painter() {
    private val paint = Paint()
    override val intrinsicSize = Size(picture.width.toFloat(), picture.height.toFloat())
    override fun applyAlpha(alpha: Float): Boolean { paint.alpha = alpha; return true }
    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean { paint.colorFilter = colorFilter; return true }
    override fun DrawScope.onDraw() = drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val checkpoint = native.save()
        try {
            native.scale(size.width / picture.width, size.height / picture.height)
            native.saveLayer(0f, 0f, picture.width.toFloat(), picture.height.toFloat(), paint.asFrameworkPaint())
            native.drawPicture(picture)
        } finally { native.restoreToCount(checkpoint) }
    }
}
