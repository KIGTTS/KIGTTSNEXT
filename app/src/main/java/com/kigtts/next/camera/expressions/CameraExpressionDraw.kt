package com.kigtts.next.camera.expressions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.view.PreviewView
import androidx.exifinterface.media.ExifInterface

internal data class CameraExpressionDraw(val effect: CompiledExpression, val frame: CameraFaceFrame) {
    fun photograph(view: PreviewView): CameraExpressionPhoto? {
        if (view.width <= 0 || view.height <= 0) return null
        val toPhoto = frame.toView(view)?.apply {
            postScale(1f / view.width, 1f / view.height)
        } ?: return null
        return CameraExpressionPhoto(effect, frame.stickerFaces, toPhoto)
    }
}

internal class CameraExpressionPhoto(
    private val effect: CompiledExpression,
    private val faces: List<ExpressionFace>,
    private val toUnitPhoto: Matrix,
) {
    fun paint(bitmap: Bitmap, exif: ExifInterface) {
        val rawToUpright = Matrix().apply {
            if (exif.isFlipped) setScale(-1f, 1f)
            postRotate(exif.rotationDegrees.toFloat())
        }
        val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        rawToUpright.mapRect(bounds)
        rawToUpright.postTranslate(-bounds.left, -bounds.top)
        val uprightToRaw = Matrix()
        check(rawToUpright.invert(uprightToRaw))
        Canvas(bitmap).apply {
            concat(uprightToRaw)
            scale(bounds.width(), bounds.height())
            concat(toUnitPhoto)
            effect.draw(this, faces)
        }
    }
}
