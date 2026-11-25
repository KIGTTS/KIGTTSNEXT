package com.kigtts.next.camera.expressions

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import cn.kigarface.prototype.FaceSurfaceSamples
import kotlin.math.atan2
import kotlin.math.hypot

/** Geometry and sampled color use the supplied FringeGuard and MemeFaceArt coordinate system. */
internal class ExpressionFaceSurface(private val color: Int, private val x: Float, private val y: Float,
    private val dx: Float, private val dy: Float, private val height: Float, edge: FloatArray?) {
    private val cutout = edge?.takeIf { it.size > 1 }?.let { fringe ->
        Path().apply {
            fun point(u: Float, v: Float, start: Boolean = false) {
                val fx = u * .88f
                val fy = v * height * .74f
                val px = x + dx * fx - dy * fy
                val py = y + dy * fx + dx * fy
                if (start) moveTo(px, py) else lineTo(px, py)
            }
            fringe.forEachIndexed { index, value ->
                point(-1.08f + 2.16f * index / fringe.lastIndex, value, index == 0)
            }
            point(1.08f, -3f)
            point(-1.08f, -3f)
            close()
        }
    }
    private val feathers = RadialGradient(0f, 0f, 1f,
        intArrayOf(color, color, color and 0x00ffffff), floatArrayOf(0f, .83f, 1f), Shader.TileMode.CLAMP)

    fun clip(canvas: Canvas) { cutout?.let { canvas.clipOutPath(it) } }

    fun drawBase(canvas: Canvas, alpha: Int) {
        val span = hypot(dx, dy)
        val save = canvas.save()
        canvas.translate(x, y)
        canvas.rotate(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat())
        canvas.scale(span * .88f, span * height * .74f)
        canvas.translate(0f, .23f)
        canvas.scale(1.04f, 1.12f)
        canvas.drawCircle(0f, 0f, 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = feathers
            this.alpha = alpha
        })
        canvas.restoreToCount(save)
    }

    companion object {
        fun from(data: FaceSurfaceSamples.Surface) =
            ExpressionFaceSurface(data.color(), data.x(), data.y(), data.dx(), data.dy(), data.height(), data.fringe())

        val SAMPLE = ExpressionFaceSurface(0xffeed3c5.toInt(), 160f, 125f, 78f, 0f, 1.25f,
            floatArrayOf(-1.1f, -.89f, -.75f, -.8f, -.9f, -.52f, -.20f, -.37f, -.70f, -.78f, -1.1f))
    }
}
