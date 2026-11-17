package com.kigtts.next.identity

import android.graphics.*
import android.text.Layout
import androidx.core.content.ContextCompat
import com.google.zxing.common.BitMatrix
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.media.MediaBitmapDecoder
import com.kigtts.next.ui.identity.profileBoardGeometry
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

internal class ProfileLongTiles(private val identity: IdentityCard, width: Int, private val style: ProfileLongStyle,
    private val stickers: StickerRepository) {
    private val tiles = identity.tiles.take(24)
    private val geometry = profileBoardGeometry(tiles, width.toFloat(), 24f, 72f)
    val height = geometry.height.roundToInt()

    /** Absolute image tile bounds in the flattened long image coordinate space. */
    internal fun imageRects(left: Int, top: Int): Map<String, Rect> = tiles.asSequence()
        .filter { it.kind == ProfileTileKind.IMAGE }
        .map { tile ->
            val box = geometry.bounds.getValue(tile.id)
            tile.id to Rect((left + box.left).roundToInt(), (top + box.top).roundToInt(),
                (left + box.right).roundToInt(), (top + box.bottom).roundToInt())
        }.toMap()

    fun draw(canvas: Canvas, left: Int, top: Int) {
        for (tile in tiles) {
            val box = geometry.bounds.getValue(tile.id)
            val area = RectF(left + box.left, top + box.top, left + box.right, top + box.bottom)
            val paint = style.paint
            paint.color = if (tile.kind == ProfileTileKind.QR)
                profileQrPalette(tile.qrStyle, tile.qrKeepLight, style.dark, style.primary, style.tile).paper
                else style.tile
            if (!tile.kind.isSectionRow) canvas.drawRoundRect(area, style.radius, style.radius, paint)
            canvas.save()
            val clip = Path().apply { addRoundRect(area, style.radius, style.radius, Path.Direction.CW) }
            canvas.clipPath(clip)
            drawTile(canvas, tile, area)
            canvas.restore()
        }
    }

    private fun drawTile(canvas: Canvas, tile: ProfileTile, area: RectF) {
        val w = area.width().toInt()
        if (tile.kind == ProfileTileKind.TITLE) {
            style.text(canvas, style.layout(tile.title, 34f, style.ink, w - 16, 1, 600), area.left + 8, area.top + 8)
            return
        }
        if (tile.kind == ProfileTileKind.DIVIDER) {
            val text = tile.title.takeIf(String::isNotBlank)?.let {
                style.layout(it, 34f, style.ink, w / 2, 1, 600, Layout.Alignment.ALIGN_CENTER)
            }
            val half = text?.getLineWidth(0)?.div(2)?.plus(14f) ?: 0f
            val paint = style.paint
            paint.color = style.muted; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.4f
            fun stroke(left: Float, right: Float) = canvas.drawPath(profileDividerPath(left, right, area.centerY(),
                tile.dividerStyle == ProfileDividerStyle.WAVE, 5f, 36f), paint)
            if (text == null) stroke(area.left + 8, area.right - 8)
            else {
                stroke(area.left + 8, area.centerX() - half); stroke(area.centerX() + half, area.right - 8)
                style.text(canvas, text, area.centerX() - text.width / 2f, area.centerY() - text.height / 2f)
            }
            paint.style = Paint.Style.FILL
            return
        }
        if (tile.kind == ProfileTileKind.QR) {
            val code = profileQrMatrix(tile.text)
            if (code != null) {
                val inset = RectF(area).apply { inset(16f, 16f) }
                val color = profileQrPalette(tile.qrStyle, tile.qrKeepLight, style.dark, style.primary, style.tile)
                val qr = RectF(inset)
                if (tile.title.isNotBlank() && inset.width() > inset.height() + 24) {
                    qr.right = qr.left + qr.height()
                    val heading = style.layout(tile.title, 28f, color.ink, (inset.right - qr.right - 16).toInt(), 2, 500)
                    style.text(canvas, heading, qr.right + 16, inset.centerY() - heading.height / 2f)
                } else if (tile.title.isNotBlank() && inset.height() > inset.width() + 24) {
                    qr.bottom = qr.top + qr.width()
                    val heading = style.layout(tile.title, 28f, color.ink, inset.width().toInt(), 2, 500, Layout.Alignment.ALIGN_CENTER)
                    style.text(canvas, heading, inset.left, (inset.bottom + qr.bottom - heading.height) / 2f)
                }
                drawProfileLongQr(canvas, code, qr, style.paint, color.ink, color.paper, tile.qrStyle == ProfileQrStyle.DOTS)
            }
            return
        }
        if (tile.kind == ProfileTileKind.IMAGE && drawImage(canvas, tile, area)) return
        if (tile.isTitleOnlyText || tile.kind == ProfileTileKind.IMAGE) {
            val title = if (tile.kind == ProfileTileKind.IMAGE) tile.title.ifBlank { "图片" } else tile.title
            val heading = style.layout(title, if (tile.kind == ProfileTileKind.IMAGE) 28f else 44f, style.ink,
                w - 32, tile.rows * 2, 500, Layout.Alignment.ALIGN_CENTER)
            val glyphRoom = if (tile.kind == ProfileTileKind.IMAGE) 50f else 0f
            val y = area.centerY() - (heading.height + glyphRoom) / 2
            if (glyphRoom > 0) imageGlyph(canvas, area.centerX() - 18, y, 36f)
            style.text(canvas, heading, area.left + 16, y + glyphRoom)
            return
        }
        val contact = tile.resolveContact(identity.contacts)
        val hasIcon = tile.kind == ProfileTileKind.CONTACT || tile.kind == ProfileTileKind.LINK
        val compact = tile.rows == 1
        if (hasIcon && tile.columns == 1 && compact) {
            glyph(canvas, contact, area.centerX() - 24f, area.centerY() - 24f, 48, tile.kind)
            return
        }
        val title = tile.title.ifBlank { contact?.label ?: if (tile.kind == ProfileTileKind.STATUS) "状态" else "" }
        val detail = if (tile.kind == ProfileTileKind.CONTACT) contact?.value ?: "联系方式已移除" else tile.text
        val iconSpace = if (hasIcon) 60 else 0
        val textWidth = w - 48 - if (compact) iconSpace else 0
        val header = style.layout(title, 28f, style.ink, textWidth, if (compact) 1 else 2, 500)
        val body = style.layout(detail, if (compact) 24f else 28f, style.muted, textWidth, if (compact) 1 else tile.rows * 2)
        val between = if (title.isNotBlank() && detail.isNotBlank()) 4 else 0
        var y = area.centerY() - (header.height + body.height + between + if (compact) 0 else iconSpace) / 2f
        if (hasIcon) {
            glyph(canvas, contact, area.left + 24, if (compact) area.centerY() - 24 else y, 48, tile.kind)
            if (!compact) y += iconSpace
        }
        val x = area.left + 24 + if (compact) iconSpace else 0
        if (title.isNotBlank()) style.text(canvas, header, x, y)
        if (detail.isNotBlank()) style.text(canvas, body, x, y + header.height + between)
    }

    private fun drawImage(canvas: Canvas, tile: ProfileTile, area: RectF): Boolean {
        val file = profileImageFile(stickers, tile.image) ?: return false
        val bitmap = runCatching { MediaBitmapDecoder.decode(file, 1600) }.getOrNull() ?: return false
        try {
            val scale = max(area.width() / bitmap.width, area.height() / bitmap.height)
            val halfWidth = bitmap.width * scale / 2
            val halfHeight = bitmap.height * scale / 2
            val destination = RectF(area.centerX() - halfWidth, area.centerY() - halfHeight,
                area.centerX() + halfWidth, area.centerY() + halfHeight)
            canvas.drawBitmap(bitmap, null, destination, imagePaint)
            return true
        } finally { bitmap.recycle() }
    }

    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun glyph(canvas: Canvas, contact: ContactLink?, x: Float, y: Float, edge: Int, kind: ProfileTileKind) {
        val icon = if (kind == ProfileTileKind.LINK) SocialPlatform.LINK.icon
            else socialPlatform(contact ?: ContactLink("", "")).icon
        ContextCompat.getDrawable(style.context, icon)?.mutate()?.let {
            it.setTint(style.ink); it.setBounds(x.toInt(), y.toInt(), x.toInt() + edge, y.toInt() + edge); it.draw(canvas)
        }
    }

    private fun imageGlyph(canvas: Canvas, left: Float, top: Float, edge: Float) {
        val p = style.paint
        p.color = style.muted; p.style = Paint.Style.STROKE; p.strokeWidth = 3f
        canvas.drawRoundRect(left, top, left + edge, top + edge, 5f, 5f, p)
        val mountain = Path().apply {
            moveTo(left + 5, top + edge - 8); lineTo(left + 14, top + 16)
            lineTo(left + 22, top + 23); lineTo(left + edge - 5, top + 13)
        }
        canvas.drawPath(mountain, p); p.style = Paint.Style.FILL
    }
}

internal fun drawProfileLongQr(canvas: Canvas, code: BitMatrix, area: RectF, paint: Paint, ink: Int = Color.BLACK,
    paper: Int = Color.WHITE, dots: Boolean = false) {
    val step = floor(minOf(area.width(), area.height()) / code.width)
    if (step < 1) return
    val side = step * code.width
    val left = floor(area.centerX() - side / 2f)
    val top = floor(area.centerY() - side / 2f)
    paint.color = paper
    canvas.drawRect(left, top, left + side, top + side, paint)
    paint.color = ink
    for (y in 0 until code.height) for (x in 0 until code.width) if (code[x, y]) {
        val px = left + x * step; val py = top + y * step
        if (dots && step >= 3 && code.qrDotAllowed(x, y))
            canvas.drawCircle(px + step / 2, py + step / 2, step * .48f, paint)
        else canvas.drawRect(px, py, px + step, py + step, paint)
    }
}
