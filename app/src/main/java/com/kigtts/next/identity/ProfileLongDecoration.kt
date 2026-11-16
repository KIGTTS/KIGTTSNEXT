package com.kigtts.next.identity

import android.graphics.*
import android.text.StaticLayout
import kotlin.math.ceil

internal class ProfileLongTags(tags: List<String>, private val style: ProfileLongStyle, width: Int) {
    private data class Tag(val x: Int, val y: Int, val w: Int, val h: Int, val label: StaticLayout)
    private val chips = buildList {
        var x = 0; var y = 0; var row = 0
        for (tag in tags.take(12)) {
            val label = style.layout(tag, 27f, style.tagInk, width - 32, 1, 500)
            val w = (ceil(label.getLineWidth(0)).toInt() + 32).coerceAtMost(width)
            val h = label.height + 18
            if (x + w > width) { x = 0; y += row + 12; row = 0 }
            add(Tag(x, y, w, h, label))
            x += w + 12
            row = maxOf(row, h)
        }
    }
    val height = chips.maxOfOrNull { it.y + it.h } ?: 0

    fun draw(canvas: Canvas, left: Int, top: Int) = chips.forEach {
        val x = (left + it.x).toFloat()
        val y = (top + it.y).toFloat()
        style.paint.color = style.tagBackground
        canvas.drawRoundRect(x, y, x + it.w, y + it.h, it.h / 2f, it.h / 2f, style.paint)
        style.text(canvas, it.label, x + 16, y + 9)
    }
}

internal const val PROFILE_SIGNATURE_HEIGHT = 64

/** The wordmark and fingerprint sit above the full-bleed data strip. */
internal fun drawProfileCodeSignature(canvas: Canvas, top: Int, code: ProfileGrayCode.Code, style: ProfileLongStyle) {
    val centerY = top + PROFILE_SIGNATURE_HEIGHT / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = style.muted
        textSize = 21f
        typeface = Typeface.MONOSPACE
    }
    val logoWidth = 128
    val gap = 20
    val barCount = 16
    val barStep = 7
    val width = logoWidth + gap * 2 + barCount * barStep + paint.measureText(code.identifier)
    val left = ((ProfileGrayCode.WIDTH - width) / 2).toInt()
    style.context.getDrawable(com.kigtts.next.R.drawable.logo_horizontal)?.mutate()?.let { logo ->
        val logoHeight = logoWidth / 4
        val y = centerY.toInt() - logoHeight / 2
        logo.setTint(style.ink)
        logo.setBounds(left, y, left + logoWidth, y + logoHeight)
        logo.draw(canvas)
    }
    val waveLeft = left + logoWidth + gap
    paint.color = style.primary
    repeat(barCount) { i ->
        val x = waveLeft + i * barStep.toFloat()
        val half = 4f + 3f * code.signatureLevel(i)
        canvas.drawRoundRect(x, centerY - half, x + 4, centerY + half, 2f, 2f, paint)
    }
    paint.color = style.muted
    val baseline = centerY - (paint.ascent() + paint.descent()) / 2
    canvas.drawText(code.identifier, (waveLeft + barCount * barStep + gap).toFloat(), baseline, paint)
}
