package com.kigtts.next.identity

import android.graphics.*
import androidx.core.content.ContextCompat
import com.kigtts.next.cards.StickerRepository

/** Software export mirrors the five icons and horizontal overflow mark used on the native card. */
internal fun drawIdentityContacts(canvas: Canvas, identity: IdentityCard, stickers: StickerRepository, height: Float) {
    val iconSize = (height * .085f).toInt().coerceAtLeast(1)
    val links = identity.contacts.take(5).map { it as ContactLink? } + if (identity.contacts.size > 5) listOf(null) else emptyList()
    links.forEachIndexed { index, link ->
        val buttonSize = iconSize * 32f / 22f; val gap = buttonSize * .125f
        val buttonLeft = height * .04f + index * (buttonSize + gap); val buttonTop = height * .04f
        val left = buttonLeft + (buttonSize - iconSize) / 2; val top = buttonTop + (buttonSize - iconSize) / 2
        val outer = buttonSize / 2; val inner = buttonSize * .11f
        val start = if (index == 0) outer else inner; val end = if (index == links.lastIndex) outer else inner
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = 0x5C000000 }
        val shape = Path().apply { addRoundRect(RectF(buttonLeft, buttonTop, buttonLeft + buttonSize, buttonTop + buttonSize),
            floatArrayOf(start, start, end, end, end, end, start, start), Path.Direction.CW) }
        canvas.drawPath(shape, p)
        p.color = Color.WHITE
        val glyph = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        try {
            val drawing = Canvas(glyph)
            if (link != null) ContextCompat.getDrawable(stickers.context, socialPlatform(link).icon)?.mutate()?.let {
                it.setTint(Color.WHITE); it.setBounds(0, 0, iconSize, iconSize); it.draw(drawing)
            } else {
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                listOf(.25f, .5f, .75f).forEach { drawing.drawCircle(iconSize * it, iconSize / 2f, iconSize * .075f, dot) }
            }
            p.setShadowLayer(iconSize * .09f, 0f, iconSize * .045f, 0xBF000000.toInt())
            canvas.drawBitmap(glyph, left, top, p)
        } finally { glyph.recycle() }
    }
}
