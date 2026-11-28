package com.kigtts.next.ui.cards.camera

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.kigtts.next.camera.expressions.ExpressionFace
import com.kigtts.next.camera.expressions.ExpressionPoint
import com.kigtts.next.camera.expressions.EXPRESSION_LAYER_EDGE
import com.kigtts.next.media.UiIcons

internal const val EXPRESSION_PREVIEW_EDGE = EXPRESSION_LAYER_EDGE
internal const val EXPRESSION_PORTRAIT_SCRIM = .72f

// These landmarks are in the artwork's 1536 px viewBox, with left and right in screen order.
internal val expressionPreviewFace = ExpressionFace(
    0, RectF(70f, 104f, 251f, 278f), 1f, 1f,
    portraitPoint(507f, 852f), portraitPoint(1027f, 849f),
    portraitPoint(750f, 993f), portraitPoint(744f, 1156f), portraitPoint(786f, 1156f),
)

private fun portraitPoint(x: Float, y: Float): ExpressionPoint {
    val scale = EXPRESSION_PREVIEW_EDGE / 1536f
    return ExpressionPoint(x * scale, y * scale)
}

internal object ExpressionPreviewPortrait {
    private val previews = mutableMapOf<Boolean, Bitmap>()

    @Synchronized
    fun load(assets: AssetManager, withoutFeatures: Boolean = false): Bitmap = previews.getOrPut(withoutFeatures) {
        val file = if (withoutFeatures) "preview-avatar-faceless.svg" else "preview-avatar.svg"
        assets.open("camera-expressions/$file").bufferedReader(Charsets.UTF_8).use { reader ->
            UiIcons.renderSvg(reader.readText(), EXPRESSION_PREVIEW_EDGE)
        }
    }
}

private val portraitPaint = Paint(Paint.FILTER_BITMAP_FLAG)
private val portraitBounds = RectF(0f, 0f, EXPRESSION_PREVIEW_EDGE.toFloat(), EXPRESSION_PREVIEW_EDGE.toFloat())

internal fun drawExpressionPortrait(canvas: Canvas, portrait: Bitmap, scrim: Paint? = null) {
    canvas.drawBitmap(portrait, null, portraitBounds, portraitPaint)
    scrim?.let { canvas.drawRect(portraitBounds, it) }
}
