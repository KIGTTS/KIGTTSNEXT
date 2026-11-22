package com.kigtts.next.identity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.text.Layout
import com.kigtts.next.i18n.uiCopy
import kotlin.math.roundToInt

/** The recoverable image areas never overlap text or decorative cropping. */
internal class ProfileLongHeader(identity: IdentityCard, private val style: ProfileLongStyle,
    private val banner: Bitmap, private val avatar: Bitmap?, width: Int, padding: Int) {
    private val avatarEdge = 156
    val coverRect = Rect(0, 0, width, (width * banner.height.toFloat() / banner.width).roundToInt())
    private val textLeft = padding + avatarEdge + 24
    private val textWidth = width - padding - textLeft
    private val displayName = identity.nickname.ifBlank { style.context.uiCopy("profile.export_user") }
    private val name = style.layout(displayName, 54f, style.ink, textWidth, 2, 700)
    private val signature = identity.bio.takeIf(String::isNotBlank)?.let { style.layout(it, 32f, style.muted, textWidth, 6) }
    private val textHeight = name.height + (signature?.let { it.height + 8 } ?: 0)
    private val rowHeight = maxOf(avatarEdge, textHeight)
    private val rowTop = coverRect.bottom + padding
    private val avatarTop = rowTop + (rowHeight - avatarEdge) / 2
    private val avatarBounds = Rect(padding, avatarTop, padding + avatarEdge, avatarTop + avatarEdge)
    val avatarRect = avatarBounds.takeIf { avatar != null }
    val height = rowTop + rowHeight + padding

    fun draw(canvas: Canvas) {
        canvas.drawBitmap(banner, null, coverRect, style.paint)
        if (avatar != null) {
            val edge = minOf(avatar.width, avatar.height)
            val left = (avatar.width - edge) / 2
            val top = (avatar.height - edge) / 2
            canvas.drawBitmap(avatar, Rect(left, top, left + edge, top + edge), avatarBounds, style.paint)
        } else {
            style.paint.color = style.tagBackground
            canvas.drawRect(avatarBounds, style.paint)
            val initial = displayName.substring(0, displayName.offsetByCodePoints(0, 1))
            val glyph = style.layout(initial, 58f, style.tagInk, avatarEdge, 1, 700, Layout.Alignment.ALIGN_CENTER)
            style.text(canvas, glyph, avatarBounds.left.toFloat(), avatarTop + (avatarEdge - glyph.height) / 2f)
        }
        val top = rowTop + (rowHeight - textHeight) / 2f
        style.text(canvas, name, textLeft.toFloat(), top)
        signature?.let { style.text(canvas, it, textLeft.toFloat(), top + name.height + 8) }
    }
}
