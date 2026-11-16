package com.kigtts.next.identity

import android.graphics.*
import android.text.StaticLayout
import com.kigtts.next.R
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

internal const val PROFILE_WORDMARK_HEIGHT = 64

internal fun drawProfileWordmark(canvas: Canvas, top: Int, style: ProfileLongStyle) {
    style.context.getDrawable(R.drawable.logo_kigtts_wordmark)?.mutate()?.let { logo ->
        val width = 152
        val height = width * logo.intrinsicHeight / logo.intrinsicWidth
        val x = (ProfileGrayCode.WIDTH - width) / 2
        val y = top + (PROFILE_WORDMARK_HEIGHT - height) / 2
        logo.setTint(style.ink)
        logo.setBounds(x, y, x + width, y + height)
        logo.draw(canvas)
    }
}
