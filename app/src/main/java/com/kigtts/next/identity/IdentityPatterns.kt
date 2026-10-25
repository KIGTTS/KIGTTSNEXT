package com.kigtts.next.identity

import android.content.Context
import android.graphics.*
import com.kigtts.next.R
import com.kigtts.next.ui.components.MaterialSymbols
import kotlin.math.*

/** Monochrome, deterministic, right-weighted vector artwork; no raster pattern assets. */
internal object IdentityPatterns {
    val names = linkedMapOf("" to "无", "contours" to "等高线", "grid" to "栅格", "dots" to "点阵",
        "halftone" to "半调", "logo" to "KIGTTS", "faces" to "表情", "pcb" to "电路")
    fun draw(canvas: Canvas, context: Context, kind: String, width: Int, height: Int, light: Boolean) {
        if (kind !in names || kind.isEmpty()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (light) Color.BLACK else Color.WHITE; alpha = 40; strokeWidth = 1.3f }
        canvas.save(); canvas.scale(width / 1000f, height / 562.5f)
        canvas.clipRect(400f, 0f, 1000f, 562.5f)
        when (kind) {
            "grid" -> { p.style = Paint.Style.STROKE
                for (x in 440..1020 step 44) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), 563f, p)
                for (y in 0..600 step 44) canvas.drawLine(400f, y.toFloat(), 1000f, y.toFloat(), p) }
            "dots", "halftone" -> for (x in 440..1040 step 32) for (y in -16..600 step 32) {
                val radius = if (kind == "dots") 2.8f else 2f + 10f * ((x - 440) / 600f) * (.5f + .5f * sin(y / 110f))
                canvas.drawCircle(x.toFloat(), y.toFloat(), radius, p)
            }
            "contours" -> { p.style = Paint.Style.STROKE
                repeat(18) { ring -> val path = Path()
                    repeat(161) { i -> val a = i * 2 * PI / 160; val r = 22 + ring * 21 + 14 * sin(3 * a + ring * .17)
                        val x = (900 + cos(a) * r).toFloat(); val y = (220 + sin(a) * r * .8).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    path.close(); canvas.drawPath(path, p) } }
            "pcb" -> { p.style = Paint.Style.STROKE
                repeat(12) { i -> val x = 510f + i * 42; val y = 40f + (i % 5) * 97
                    val path = Path().apply { moveTo(x, -30f); lineTo(x, y); lineTo(x + 55, y + 55); lineTo(1030f, y + 55) }
                    canvas.drawPath(path, p); canvas.drawCircle(x, y, 7f, p); canvas.drawCircle(x + 76, y + 55, 4f, p) } }
            "logo" -> context.getDrawable(R.drawable.logo_horizontal)?.mutate()?.let {
                it.setTint(if (light) Color.BLACK else Color.WHITE); it.alpha = 40
                canvas.save(); canvas.rotate(-18f, 800f, 260f); it.setBounds(520, 180, 1100, 320); it.draw(canvas); canvas.restore() }
            "faces" -> { val faces = listOf("sentiment_very_satisfied", "sentiment_satisfied", "sentiment_excited", "mood", "sentiment_calm")
                for (row in 0..4) for (col in 0..4) MaterialSymbols.androidPath(faces[(row * 3 + col) % faces.size])?.let {
                    canvas.save(); canvas.translate(450f + col * 126 + (row % 2) * 30, -20f + row * 135)
                    canvas.rotate(((row + col) % 3 - 1) * 15f, 48f, 48f); canvas.scale(4f, 4f)
                    p.style = if ((row + col) % 2 == 0) Paint.Style.FILL else Paint.Style.STROKE
                    p.strokeWidth = .4f; canvas.drawPath(it, p); canvas.restore() } }
        }
        canvas.restore()
    }
}
