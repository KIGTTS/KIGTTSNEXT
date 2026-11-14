package com.kigtts.next.identity

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kigtts.next.cards.StickerRepository
import java.io.ByteArrayOutputStream
import java.io.File

/** Offline, portable personal homepage. Previews and contact QR codes live in linked appendix pages. */
internal object ProfilePdfExport {
    fun write(identity: IdentityCard, stickers: StickerRepository, directory: File): File {
        directory.mkdirs()
        val banner = exportIdentityCard(identity, stickers, directory)
        val images = identity.tiles.filter { it.kind == ProfileTileKind.IMAGE }.mapNotNull { tile ->
            profileImageFile(stickers, tile.image)?.let { tile to it }
        }
        val contacts = (identity.contacts + identity.tiles.mapNotNull { it.social }).distinctBy(::contactPayload).filter { contactPayload(it).isNotBlank() }
        val pages = mutableListOf<ProfilePdfPage>()
        var page = Sheet("home")
        fun next() { pages += page.finish(); page = Sheet("main-${pages.size}") }
        fun space(height: Float) { if (page.y + height > 780) next() }
        page.image(banner, RectF(32f, 32f, 563f, 331f)); page.y = 358f
        fun paragraph(text: String, size: Float, bold: Boolean = false) {
            if (text.isBlank()) return
            text.chunked(350).forEach { chunk -> space(page.height(chunk, size, bold)); page.paragraph(chunk, size, bold) }
        }
        paragraph(identity.nickname.ifBlank { "KIGTTS 用户" }, 26f, true)
        paragraph(identity.bio, 13f)
        if (identity.tags.isNotEmpty()) paragraph(identity.tags.joinToString("   ") { "# $it" }, 12f)
        contacts.forEachIndexed { index, contact ->
            space(76f)
            val top = page.y
            page.box(top, 62f)
            page.paragraph(contact.label.ifBlank { "联系方式" }, 14f, true, 48f)
            page.paragraph(contact.value.take(100) + "   · 查看二维码", 11f, x = 48f, maxLines = 2)
            page.links += ProfilePdfLink(RectF(32f, top, 563f, top + 62f), page = "contact-$index")
            page.y = top + 72f
        }
        identity.tiles.forEach { tile ->
            when (tile.kind) {
                ProfileTileKind.IMAGE -> images.firstOrNull { it.first.id == tile.id }?.let { (_, file) ->
                    space(164f); val top = page.y; page.box(top, 152f); page.y = top
                    page.image(file, RectF(40f, page.y + 8, 555f, page.y + 142))
                    page.links += ProfilePdfLink(RectF(32f, page.y, 563f, page.y + 152f), page = "image-${tile.id}")
                    page.y += 164f
                }
                ProfileTileKind.CONTACT -> if (tile.social == null) {
                    paragraph(tile.title, 15f, true); paragraph(tile.text.ifBlank { tile.contact }, 12f)
                }
                ProfileTileKind.QR -> profileQrMatrix(tile.text)?.let { code ->
                    space(224f)
                    page.paragraph(tile.title.ifBlank { "二维码" }, 15f, true)
                    val pitch = kotlin.math.floor(172f / code.width)
                    val side = pitch * code.width; val left = (595f - side) / 2f
                    val paint = Paint().apply { color = Color.WHITE }
                    page.canvas.drawRect(left, page.y, left + side, page.y + side, paint)
                    paint.color = Color.BLACK
                    for (y in 0 until code.height) for (x in 0 until code.width) if (code[x, y]) {
                        val px = left + x * pitch; val py = page.y + y * pitch
                        if (tile.qrStyle == ProfileQrStyle.DOTS && pitch >= 3f && code.qrDotAllowed(x, y))
                            page.canvas.drawCircle(px + pitch / 2, py + pitch / 2, pitch * .48f, paint)
                        else page.canvas.drawRect(px, py, px + pitch, py + pitch, paint)
                    }
                    page.y += side + 12f
                }
                else -> {
                    val body = tile.text.ifBlank { tile.contact }
                    space(page.height(tile.title, 15f, true) + if (body.isNotBlank()) 70f else 12f)
                    if (tile.title.isNotBlank()) paragraph(tile.title, if (tile.kind == ProfileTileKind.TITLE) 18f else 15f, true)
                    val text = tile.text.ifBlank { tile.contact }
                    if (tile.kind == ProfileTileKind.LINK && text.isNotBlank()) {
                        space(80f); val top = page.y; page.paragraph(text.take(120), 12f, maxLines = 3)
                        page.links += ProfilePdfLink(RectF(32f, top, 563f, page.y), url = text)
                    } else paragraph(text, 12f)
                    page.y += 12
                }
            }
        }
        pages += page.finish()
        contacts.forEachIndexed { index, contact ->
            val sheet = Sheet("contact-$index"); sheet.paragraph(contact.label.ifBlank { "联系方式" }, 25f, true)
            sheet.paragraph(contact.value, 14f)
            val matrix = QRCodeWriter().encode(contactPayload(contact), BarcodeFormat.QR_CODE, 720, 720,
                mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"))
            val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(720 * 720) { i -> if (matrix[i % 720, i / 720]) Color.BLACK else Color.WHITE }
            bitmap.setPixels(pixels, 0, 720, 0, 0, 720, 720)
            sheet.canvas.drawBitmap(bitmap, null, RectF(80f, 210f, 515f, 645f), Paint())
            bitmap.recycle()
            sheet.links += ProfilePdfLink(RectF(70f, 190f, 525f, 670f), url = contactPayload(contact))
            sheet.back(); pages += sheet.finish()
        }
        images.forEach { (tile, file) ->
            val sheet = Sheet("image-${tile.id}"); sheet.paragraph(tile.title.ifBlank { "图片预览" }, 22f, true)
            sheet.image(file, RectF(32f, 100f, 563f, 748f)); sheet.back(); pages += sheet.finish()
        }
        return File(directory, "profile.pdf").also { ProfilePdfWriter.write(it, pages) }
    }

    private class Sheet(private val id: String) {
        private val bitmap = Bitmap.createBitmap(1190, 1684, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { scale(2f, 2f); drawColor(0xfff5faf9.toInt()) }
        val links = mutableListOf<ProfilePdfLink>()
        var y = 48f
        fun height(value: String, size: Float, bold: Boolean) = layout(value, size, bold, 32f, 20).height + 12f
        private fun layout(value: String, size: Float, bold: Boolean, x: Float, maxLines: Int): StaticLayout {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff1c3f40.toInt(); textSize = size; typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL) }
            return StaticLayout.Builder.obtain(value, 0, value.length, paint, (563 - x).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setLineSpacing(4f, 1f).setMaxLines(maxLines).build()
        }
        fun paragraph(value: String, size: Float, bold: Boolean = false, x: Float = 32f, maxLines: Int = 20) {
            if (value.isBlank()) return
            val layout = layout(value, size, bold, x, maxLines)
            canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore(); y += layout.height + 12
        }
        fun box(top: Float, height: Float) { canvas.drawRoundRect(32f, top, 563f, top + height, 16f, 16f, Paint().apply { color = Color.WHITE }); y += 10 }
        fun image(file: File, area: RectF) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
            bounds.inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1600).coerceAtLeast(1); bounds.inJustDecodeBounds = false
            val source = BitmapFactory.decodeFile(file.path, bounds) ?: return
            try {
                val scale = minOf(area.width() / source.width, area.height() / source.height)
                val width = source.width * scale; val height = source.height * scale
                canvas.drawBitmap(source, null, RectF(area.centerX() - width / 2, area.centerY() - height / 2, area.centerX() + width / 2, area.centerY() + height / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            } finally { source.recycle() }
        }
        fun back() { y = 780f; paragraph("返回个人主页  ↗", 12f); links += ProfilePdfLink(RectF(32f, 774f, 270f, 812f), page = "home") }
        fun finish(): ProfilePdfPage {
            val data = ByteArrayOutputStream(); check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, data)); bitmap.recycle()
            return ProfilePdfPage(id, data.toByteArray(), links.toList())
        }
    }
}
