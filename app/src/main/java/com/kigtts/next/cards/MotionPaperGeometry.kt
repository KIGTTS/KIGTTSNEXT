package com.kigtts.next.cards

import android.graphics.RectF
import com.kigtts.plugin.api.PolaroidCard
import com.kigtts.plugin.api.PolaroidFrameStyle

internal data class MotionPaperGeometry(val width: Int, val height: Int, val photo: RectF, val radius: Float) {
    fun foreground(card: PolaroidCard, inputWidth: Int, inputHeight: Int): RectF {
        val scale = (minOf(photo.width() / inputWidth, photo.height() / inputHeight) * card.photoZoom).toFloat()
        val width = inputWidth * scale; val height = inputHeight * scale
        val x = photo.left + (photo.width() * card.photoX).toFloat(); val y = photo.top + (photo.height() * card.photoY).toFloat()
        return RectF(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
    }
    companion object {
        fun create(frame: PolaroidFrameStyle, width: Int, height: Int, includeFrame: Boolean): MotionPaperGeometry {
            val geometry = frame.format.geometry
            val photo = if (includeFrame) RectF((geometry.photoLeft / geometry.paperWidth * width).toFloat(),
                (geometry.photoTop / geometry.paperHeight * height).toFloat(), (geometry.photoRight / geometry.paperWidth * width).toFloat(),
                (geometry.photoBottom / geometry.paperHeight * height).toFloat()) else RectF(0f, 0f, width.toFloat(), height.toFloat())
            return MotionPaperGeometry(width, height, photo, (frame.photoCornerRadius * width).toFloat())
        }
    }
}
