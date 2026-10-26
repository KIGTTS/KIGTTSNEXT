package com.kigtts.next.identity

import android.graphics.*
import androidx.core.content.ContextCompat
import com.kigtts.next.cards.StickerRepository

/** Software export mirrors the five icons and horizontal overflow mark used on the native card. */
internal fun drawIdentityContacts(canvas: Canvas, identity: IdentityCard, stickers: StickerRepository, height: Float) {
    val iconSize = (height * .085f).toInt().coerceAtLeast(1)
    val links = identity.contacts.take(5).map { it as ContactLink? } + if (identity.contacts.size > 5) listOf(null) else emptyList()
    links.forEachIndexed { index, link ->
        val left = height * .06f + index * height * .15f; val top = height * .06f
        val x = left + iconSize / 2f; val y = top + iconSize / 2f; val radius = iconSize * 40f / 44f
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = RadialGradient(x, y, radius, intArrayOf(0x85000000.toInt(), 0x57000000, 0x1A000000, Color.TRANSPARENT),
                floatArrayOf(0f, .55f, .85f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(x, y, radius, p); p.shader = null
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
