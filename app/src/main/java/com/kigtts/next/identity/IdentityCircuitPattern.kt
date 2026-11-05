package com.kigtts.next.identity

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.random.Random

/** Sparse bundles, angled routes and staggered contacts on the right of the identity card. */
internal object IdentityCircuitPattern {
    fun draw(canvas: Canvas, paint: Paint) {
        val random = Random(1971)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        fun trace(vararg xy: Float) {
            paint.strokeWidth = 1.6f + random.nextFloat() * 1.2f
            val path = Path().apply {
                moveTo(xy[0], xy[1])
                for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
            }
            canvas.drawPath(path, paint)
        }
        fun via(x: Float, y: Float, wide: Boolean) {
            paint.strokeWidth = 2f
            canvas.drawCircle(x, y, if (wide) 6f else 4.25f, paint)
            if (wide) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, 1.75f, paint)
                paint.style = Paint.Style.STROKE
            }
        }

        // Fan out from the top edge. The inner tracks end higher to leave each other clear.
        repeat(6) { lane ->
            val x = 658f + lane * 38f + random.nextFloat() * 8f
            val y = 202f - lane * 25f + random.nextFloat() * 6f
            val cut = 23f + random.nextFloat() * 17f
            val end = if (lane in 1..4 && lane != 3) x + cut + 48f + random.nextFloat() * 38f else 1020f
            val terminal = end < 1000f
            trace(x, -12f, x, y - cut, x + cut, y, end - if (terminal) 6f else 0f, y)
            if (terminal) via(end, y, true)
        }
        // Two layers of staggered side contacts; lane widths leave room for the jogs.
        repeat(7) { lane ->
            val y = 238f + lane * 27f + random.nextFloat() * 5f
            val end = 580f + random.nextFloat() * 224f
            val bend = 829f + random.nextFloat() * 47f
            val jog = if (lane % 3 == 1) 0f else 9f + random.nextFloat() * 7f
            if (jog > 0f) trace(1020f, y + jog, bend + jog, y + jog, bend, y, end + 6f, y)
            else trace(1020f, y, end + 6f, y)
            via(end, y, true)
        }
        repeat(5) { lane ->
            val x = 560f + lane * 43f + random.nextFloat() * 7f
            val y = 480f - lane * 9f + random.nextFloat() * 5f
            val cut = 11f + random.nextFloat() * 11f
            val end = x - cut - 10f - random.nextFloat() * 21f
            trace(x, 578f, x, y + cut, x - cut, y, end + 5f, y)
            via(end, y, false)
        }
        // A compact connector footprint at the bottom, with pads connected to short routes.
        repeat(4) { lane ->
            val x = 821f + lane * 30f
            val y = 478f - lane * 12f
            val bend = 929f + lane * 18f
            trace(x, y + 10f, x, y + 34f, x + 14f, y + 48f, bend - 15f, y + 48f, bend, y + 63f, bend, 581f)
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(x - 4.5f, y - 1f, x + 4.5f, y + 11f, 2f, 2f, paint)
            paint.style = Paint.Style.STROKE
        }
    }
}
