package com.kigtts.next.identity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.media.WebThemeState
import com.kigtts.next.sharing.faceWebTheme
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
        val file = exportIdentityCard(identity, stickers, directory)
        val banner = checkNotNull(BitmapFactory.decodeFile(file.path)) { "无法读取名片封面" }
        val theme = faceWebTheme(WebThemeState.current)
        fun role(name: String, fallback: Int) = theme.colors[name]?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: fallback
        val surface = role("surface", Color.WHITE)
        val tile = role("surface-container-high", 0xffe5eeee.toInt())
        val ink = role("on-surface", Color.BLACK)
        val muted = role("on-surface-variant", ink)
        val primary = role("primary", ink)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val radius = (theme.corner * 2f).coerceAtLeast(12f)
        val sections = mutableListOf<Pair<Int, (Canvas, Int) -> Unit>>()
        fun layout(text: String, size: Float, color: Int, width: Int, lines: Int, bold: Boolean = false): StaticLayout {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; textSize = size; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width).setIncludePad(false)
                .setMaxLines(lines).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        }
        fun drawText(canvas: Canvas, text: StaticLayout, x: Int, y: Int) {
            canvas.save(); canvas.translate(x.toFloat(), y.toFloat()); text.draw(canvas); canvas.restore()
        }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) {
            if (value.isBlank()) return
            val text = layout(value, size, color, INNER, 3, bold)
            sections += (text.height + 18) to { c, y -> drawText(c, text, PAD, y) }
        }
        val headerHeight = (INNER * banner.height.toFloat() / banner.width).toInt().coerceIn(1, INNER)
        val publicProfile = PublicIdentityProfile.from(identity.publicJson()).let {
            it.copy(avatar = null, tiles = it.tiles.map { tile -> tile.copy(image = "") })
        }.json().put("identityId", identity.id)
        val restoreCode = ProfileGrayCode.encode(JSONObject().put("profile", publicProfile)
            .put("cover", JSONArray(listOf(PAD, PAD, WIDTH - PAD, PAD + headerHeight))).toString())
        sections += (headerHeight + 20) to { c, y ->
            val rect = RectF(PAD.toFloat(), y.toFloat(), (WIDTH - PAD).toFloat(), (y + headerHeight).toFloat())
            val clip = android.graphics.Path().apply { addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW) }
            c.save(); c.clipPath(clip); c.drawBitmap(banner, null, rect, paint); c.restore()
        }
        label(identity.tags.joinToString("   ·   "), 24f, primary)
        identity.tiles.filter { it.kind in setOf(ProfileTileKind.TITLE, ProfileTileKind.TEXT, ProfileTileKind.STATUS) }.forEach { item ->
            label(item.title, 31f, ink, true)
            label(item.text, 25f, muted)
        }
        val links = links(identity)
        if (links.isNotEmpty()) label("链接与二维码", 34f, ink, true)
        links.forEach { entry ->
            val qr = profileQrMatrix(entry.value, maxBytes = 2500)
            val edge = maxOf(176, (qr?.width ?: 0) * 2)
            val x = PAD + 16 + edge + 20
            val width = WIDTH - PAD - 16 - x
            val title = layout(entry.labels.joinToString(" · "), 27f, ink, width, 2, true)
            val body = layout(entry.detail, 24f, muted, width, 4)
            val row = maxOf(edge, title.height + body.height + 8) + 32
            sections += (row + 8) to { c, y ->
                paint.color = tile
                c.drawRoundRect(PAD.toFloat(), y.toFloat(), (WIDTH - PAD).toFloat(), (y + row).toFloat(), radius, radius, paint)
                val left = PAD + 16
                val top = y + (row - edge) / 2
                if (qr != null) {
                    paint.color = Color.WHITE
                    c.drawRect(left.toFloat(), top.toFloat(), (left + edge).toFloat(), (top + edge).toFloat(), paint)
                    paint.color = Color.BLACK
                    val pitch = edge / qr.width
                    val inset = (edge - pitch * qr.width) / 2
                    for (dy in 0 until qr.height) for (dx in 0 until qr.width) if (qr[dx, dy]) {
                        val l = left + inset + dx * pitch
                        val t = top + inset + dy * pitch
                        c.drawRect(l.toFloat(), t.toFloat(), (l + pitch).toFloat(), (t + pitch).toFloat(), paint)
                    }
                } else drawText(c, layout("二维码不可用", 20f, muted, edge, 2), left, y + row / 2)
                val textTop = y + (row - title.height - body.height - 8) / 2
                drawText(c, title, x, textTop); drawText(c, body, x, textTop + title.height + 8)
            }
        }
        label("KIGTTS · 名片还原码", 22f, muted, true)
        val height = sections.sumOf { it.first } + PAD * 2 + restoreCode.height
        try {
            require(height in 1..21_000) { "名片内容过多，无法生成长图" }
            val image = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(image).apply { drawColor(surface) }
                var y = PAD
                sections.forEach { (height, draw) -> draw(canvas, y); y += height }
                val accent = if (identity.gradient) identity.gradientColor.toInt() else primary
                restoreCode.draw(canvas, height - PAD, accent)
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
