package com.kigtts.next.identity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import com.kigtts.next.cards.StickerRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI

/** One flat image; links stay usable as a deduplicated QR appendix. */
internal object ProfileLongImage {
    private const val WIDTH = ProfileGrayCode.WIDTH
    private const val PAD = ProfileGrayCode.MARGIN
    private const val INNER = WIDTH - PAD * 2
    private val inlineLink = Regex("""https?://[^\s<>，。；！？“”‘’]+""", RegexOption.IGNORE_CASE)

    internal data class Link(val value: String, val labels: List<String>, val detail: String)
    internal fun links(identity: IdentityCard): List<Link> {
        val links = linkedMapOf<String, Link>()
        fun add(label: String, payload: String, detail: String = payload) {
            val value = payload.trim()
            if (value.isBlank()) return
            val key = runCatching {
                val uri = URI(value)
                if (uri.host == null) value else value.replaceRange(0, uri.scheme.length, uri.scheme.lowercase())
                    .let { raw ->
                        val start = uri.scheme.length + 3
                        if (raw.regionMatches(start, uri.host, 0, uri.host.length)) raw.replaceRange(start, start + uri.host.length, uri.host.lowercase())
                        else raw
                    }
            }.getOrDefault(value)
            val previous = links[key]
            links[key] = if (previous == null) Link(value, listOf(label.ifBlank { "链接" }), detail)
                else previous.copy(labels = (previous.labels + label).filter(String::isNotBlank).distinct())
        }
        identity.contacts.forEach { add(it.label, contactPayload(it), it.value) }
        identity.tiles.forEach { tile ->
            val body = tile.text.ifBlank { tile.contact }
            when (tile.kind) {
                ProfileTileKind.LINK -> profileExportLink(body)?.let { add(tile.title, it) }
                ProfileTileKind.CONTACT -> tile.resolveContact(identity.contacts)?.let { add(tile.title.ifBlank { it.label }, contactPayload(it), it.value) }
                    ?: profileExportLink(body)?.let { add(tile.title, it) }
                ProfileTileKind.QR -> add(tile.title.ifBlank { "二维码" }, tile.text)
                ProfileTileKind.TEXT, ProfileTileKind.STATUS -> inlineLink.findAll(body).forEach { match ->
                    profileExportLink(match.value.trimEnd('.', ',', ';', '!', ')', ']', '}'))?.let { add(tile.title, it) }
                }
                else -> Unit
            }
        }
        return links.values.toList()
    }

    fun write(identity: IdentityCard, stickers: StickerRepository, directory: File, embedSource: Boolean = false): File {
        directory.mkdirs()
        val style = ProfileLongStyle(stickers.context)
        val file = exportIdentityCard(identity, stickers, directory, includeContactButtons = false, typeface = style::face)
        val banner = checkNotNull(BitmapFactory.decodeFile(file.path)) { "无法读取名片封面" }
        val paint = style.paint
        val radius = style.radius
        val sections = mutableListOf<Pair<Int, (Canvas, Int) -> Unit>>()
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) {
            if (value.isBlank()) return
            val text = style.layout(value, size, color, INNER, 3, if (bold) 600 else 400)
            sections += (text.height + 18) to { c, y -> style.text(c, text, PAD.toFloat(), y.toFloat()) }
        }
        val headerHeight = (INNER * banner.height.toFloat() / banner.width).toInt().coerceIn(1, INNER)
        val publicProfile = PublicIdentityProfile.from(identity.publicJson()).let {
            it.copy(avatar = null, tiles = it.tiles.map { tile -> tile.copy(image = "") })
        }.json().put("identityId", identity.id).put("coverButtonsBaked", false)
        val restoreCode = ProfileGrayCode.encode(JSONObject().put("profile", publicProfile)
            .put("cover", JSONArray(listOf(PAD, PAD, WIDTH - PAD, PAD + headerHeight))).toString())
        sections += (headerHeight + 20) to { c, y ->
            val rect = RectF(PAD.toFloat(), y.toFloat(), (WIDTH - PAD).toFloat(), (y + headerHeight).toFloat())
            val clip = android.graphics.Path().apply { addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW) }
            c.save(); c.clipPath(clip); c.drawBitmap(banner, null, rect, paint); c.restore()
        }
        val tags = ProfileLongTags(identity.tags, style, INNER)
        if (tags.height > 0) sections += (tags.height + 22) to { c, y -> tags.draw(c, PAD, y) }
        val tiles = ProfileLongTiles(identity, INNER, style)
        if (tiles.height > 0) sections += (tiles.height + 28) to { c, y -> tiles.draw(c, PAD, y) }
        val fadeEdge = sections.sumOf { it.first } + PAD
        val links = links(identity)
        if (links.isNotEmpty()) label("链接与二维码", 34f, style.ink, true)
        links.forEach { entry ->
            val qr = profileQrMatrix(entry.value, maxBytes = 2500)
            val edge = maxOf(176, (qr?.width ?: 0) * 2)
            val x = PAD + 16 + edge + 20
            val width = WIDTH - PAD - 16 - x
            val title = style.layout(entry.labels.joinToString(" · "), 27f, style.ink, width, 2, 500)
            val body = style.layout(entry.detail, 24f, style.muted, width, 4)
            val row = maxOf(edge, title.height + body.height + 8) + 32
            sections += (row + 8) to { c, y ->
                paint.color = style.tile
                c.drawRoundRect(PAD.toFloat(), y.toFloat(), (WIDTH - PAD).toFloat(), (y + row).toFloat(), radius, radius, paint)
                val left = PAD + 16
                val top = y + (row - edge) / 2
                if (qr != null) drawProfileLongQr(c, qr, RectF(left.toFloat(), top.toFloat(),
                    (left + edge).toFloat(), (top + edge).toFloat()), paint)
                else style.text(c, style.layout("二维码不可用", 20f, style.muted, edge, 2), left.toFloat(), y + row / 2f)
                val textTop = y + (row - title.height - body.height - 8) / 2
                style.text(c, title, x.toFloat(), textTop.toFloat())
                style.text(c, body, x.toFloat(), (textTop + title.height + 8).toFloat())
            }
        }
        val signatureTop = sections.sumOf { it.first } + PAD + 16
        val height = signatureTop + PROFILE_SIGNATURE_HEIGHT + restoreCode.height
        try {
            require(height in 1..21_000) { "名片内容过多，无法生成长图" }
            val image = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(image)
                style.backdrop(canvas, WIDTH, (fadeEdge - 160).coerceAtLeast(0), fadeEdge)
                var y = PAD
                sections.forEach { (height, draw) -> draw(canvas, y); y += height }
                val accent = if (identity.gradient) identity.gradientColor.toInt() else style.primary
                drawProfileCodeSignature(canvas, signatureTop, restoreCode, style)
                restoreCode.draw(canvas, height, accent)
                val png = File(directory, "profile-long.png").also { output ->
                    output.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
                if (!embedSource) return png
                image.recycle()
                val archive = ProfileArchive.write(identity, stickers, File(directory, "source"))
                return File(directory, "profile-with-source.png").also { ProfilePngPayload.embed(png, archive, it) }
            } finally { if (!image.isRecycled) image.recycle() }
        } finally { banner.recycle() }
    }
}
