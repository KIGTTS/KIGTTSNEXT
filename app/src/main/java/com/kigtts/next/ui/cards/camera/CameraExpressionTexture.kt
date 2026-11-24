package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.opengl.GLES20.*
import android.opengl.GLUtils
import android.util.Size
import com.kigtts.next.camera.expressions.CameraExpressionDraw
import kotlin.math.roundToInt

/** Repaint only for a new tracking result, and keep an independent transform per output. */
internal class CameraExpressionTexture(private val output: Size) : AutoCloseable {
    private val scale = (720f / maxOf(output.width, output.height)).coerceAtMost(1f)
    private val bitmap = Bitmap.createBitmap((output.width * scale).roundToInt().coerceAtLeast(1),
        (output.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val texture = IntArray(1)
    private var previous: CameraExpressionDraw? = null

    fun bind(draw: CameraExpressionDraw, sensorToOutput: Matrix) {
        val fresh = texture[0] == 0
        if (fresh) glGenTextures(1, texture, 0)
        glBindTexture(GL_TEXTURE_2D, texture[0])
        if (fresh) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        }
        if (previous == draw) return
        bitmap.eraseColor(0)
        val transform = Matrix().apply { setConcat(sensorToOutput, draw.frame.uprightToSensor) }
        val save = canvas.save()
        try {
            canvas.scale(bitmap.width.toFloat() / output.width, bitmap.height.toFloat() / output.height)
            canvas.concat(transform)
            draw.effect.draw(canvas, draw.frame.faces)
        } finally { canvas.restoreToCount(save) }
        if (fresh) GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0)
        else GLUtils.texSubImage2D(GL_TEXTURE_2D, 0, 0, 0, bitmap)
        previous = draw
    }

    override fun close() {
        glDeleteTextures(1, texture, 0)
        bitmap.recycle()
    }
}
