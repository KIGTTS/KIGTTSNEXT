package com.kigtts.next.identity

import android.content.Context
import com.kigtts.next.media.WebThemeState
import com.kigtts.next.sharing.faceWebTheme
import java.net.URI

internal fun profileArchiveHtml(identity: IdentityCard, context: Context, images: Set<Int>): String {
    val theme = faceWebTheme(WebThemeState.current)
    val name = identity.nickname.ifBlank { "KIGTTS 用户" }.html()
    val css = context.assets.open("kigtts/profile-archive.css").bufferedReader(Charsets.UTF_8).use { it.readText() }
    fun StringBuilder.preview(label: String, thumbnail: String, content: String) {
        append("<details><summary aria-label=\"${label.html()}\">$thumbnail<span class=\"close\">关闭</span></summary>")
        append("<section class=\"preview\"><article><div class=\"preview-head\"><strong>${label.html()}</strong></div>$content</article></section></details>")
    }
    fun StringBuilder.contact(link: ContactLink) {
        val label = link.label.ifBlank { "联系方式" }
        val payload = contactPayload(link)
        val open = profileExportLink(payload)?.let { "<a class=\"button\" href=\"${it.html()}\" target=\"_blank\" rel=\"noopener noreferrer\">打开链接 ↗</a>" }.orEmpty()
        preview(label, "<strong>${label.html()}</strong><span>${link.value.html()}</span>",
            profileArchiveQr(payload, ProfileQrStyle.INK) + "<p class=\"detail\">${link.value.html()}</p>$open")
    }
    return buildString {
        append("<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><meta name=\"referrer\" content=\"no-referrer\"><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src https://kigtts.invalid; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\"><title>$name · 个人主页</title><style>${theme.css()}$css</style>")
        append("<body><main id=\"home\"><header><div class=\"eyebrow\">KIGTTS · 个人主页</div><h1 class=\"sr\">$name</h1><img class=\"cover\" src=\"cover.jpg\" alt=\"$name：${identity.bio.html()}\"></header>")
        if (identity.tags.isNotEmpty()) append("<ul class=\"tags\">${identity.tags.joinToString("") { "<li>${it.html()}</li>" }}</ul>")
        append("<section class=\"tiles\" aria-label=\"主页磁贴\">")
        packProfileTiles(identity.tiles.take(24)).forEachIndexed { i, placement ->
            val tile = placement.tile
            val label = tile.title.html()
            val body = tile.text.ifBlank { tile.contact }
            val kind = tile.kind.name.lowercase()
            val width = tile.columns
            val height = tile.rows
            append("<article class=\"tile $kind\" style=\"grid-column: ${placement.column + 1}/span $width;grid-row: ${placement.row + 1}/span $height\">")
            when (tile.kind) {
                ProfileTileKind.TITLE -> append("<h2>$label</h2>")
                ProfileTileKind.CONTACT -> {
                    val link = tile.resolveContact(identity.contacts)
                    if (link != null) contact(link)
                    else append("<strong>$label</strong><span>${body.html()}</span>")
                }
                ProfileTileKind.LINK -> {
                    val link = profileExportLink(body)
                    if (link != null) append("<a href=\"${link.html()}\" target=\"_blank\" rel=\"noopener noreferrer\"><strong>${label.ifBlank { "链接" }} ↗</strong><span>${body.html()}</span></a>")
                    else append("<strong>$label</strong><span>${body.html()}</span>")
                }
                ProfileTileKind.IMAGE -> if (i in images) {
                    val alt = tile.title.ifBlank { "图片预览" }
                    val image = "<img src=\"tile-$i.jpg\" alt=\"${alt.html()}\">"
                    preview(alt, image, image)
                } else append("<strong>${label.ifBlank { "图片" }}</strong>")
                ProfileTileKind.QR -> {
                    val qr = profileArchiveQr(tile.text, tile.qrStyle)
                    preview(tile.title.ifBlank { "查看二维码" }, qr, qr + "<p class=\"detail\">${tile.text.html()}</p>")
                }
                else -> append("<strong>$label</strong><span>${body.html()}</span>")
            }
            append("</article>")
        }
        append("</section>")
        if (identity.contacts.isNotEmpty()) {
            append("<section class=\"contacts\" id=\"contacts\"><h2>联系方式</h2>")
            identity.contacts.forEach { link -> contact(link) }
            append("</section>")
        }
        append("<footer>离线名片 · 外部链接打开时需要联网</footer></main></body></html>")
    }
}

internal fun profileExportLink(value: String): String? = runCatching {
    val text = value.trim().takeIf { it.isNotEmpty() && it.none(Char::isISOControl) } ?: return null
    val uri = URI(text)
    text.takeIf { when (uri.scheme?.lowercase()) {
        "https", "http" -> !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        "mailto" -> uri.isOpaque && '@' in uri.rawSchemeSpecificPart
        else -> false
    } }
}.getOrNull()

private fun String.html(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;").replace("'", "&#39;")

private fun profileArchiveQr(value: String, style: ProfileQrStyle): String {
    val matrix = profileQrMatrix(value) ?: return "<p>无法生成此二维码</p>"
    val edge = matrix.width
    val ink = if (style == ProfileQrStyle.INK) "#111" else "var(--md-sys-color-on-primary-container,#111)"
    val inkColor = if (WebThemeState.current.dark) "#111" else ink
    return buildString {
        append("<svg viewBox=\"0 0 $edge $edge\" role=\"img\" aria-label=\"二维码\" style=\"color:$inkColor\"><rect width=\"$edge\" height=\"$edge\" fill=\"white\"/><path fill=\"currentColor\" d=\"")
        for (y in 0 until edge) for (x in 0 until edge) if (matrix[x, y] && (style != ProfileQrStyle.DOTS || !matrix.qrDotAllowed(x, y)))
            append("M$x ${y}h1v1h-1z")
        append("\"/>")
        if (style == ProfileQrStyle.DOTS) for (y in 0 until edge) for (x in 0 until edge)
            if (matrix[x, y] && matrix.qrDotAllowed(x, y)) append("<circle cx=\"${x}.5\" cy=\"${y}.5\" r=\".45\" fill=\"currentColor\"/>")
        append("</svg>")
    }
}
