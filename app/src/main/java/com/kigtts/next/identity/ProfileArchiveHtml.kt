package com.kigtts.next.identity

import com.kigtts.next.i18n.UiLanguageStore

import com.kigtts.next.i18n.uiCopy

import android.content.Context
import android.graphics.Color
import com.kigtts.next.media.WebTheme
import com.kigtts.next.media.WebThemeState
import com.kigtts.next.sharing.FaceWebIcons
import com.kigtts.next.sharing.faceWebTheme
import java.net.URI

internal fun profileArchiveHtml(identity: IdentityCard, context: Context, images: Set<Int>,
    exportTheme: WebTheme = WebThemeState.current, coverIsBackground: Boolean = false): String {
    fun copy(key: String, vararg arguments: Pair<String, String>) = context.uiCopy(key, *arguments)
    fun htmlCopy(key: String) = copy(key).html()
    val locale = UiLanguageStore(context).load().substringBefore("-x-")
    val theme = faceWebTheme(exportTheme)
    val icons = FaceWebIcons(context)
    val name = identity.nickname.ifBlank { copy("profile.export_user") }.html()
    val css = context.assets.open("kigtts/profile-archive.css").bufferedReader(Charsets.UTF_8).use { it.readText() }
    fun StringBuilder.preview(label: String, thumbnail: String, content: String) {
        append("<details><summary aria-label=\"${label.html()}\">$thumbnail<span class=\"close\">${htmlCopy("action.close")}</span></summary>")
        append("<section class=\"preview\"><article><div class=\"preview-head\"><strong>${label.html()}</strong></div>$content</article></section></details>")
    }
    fun StringBuilder.contact(link: ContactLink, iconOnly: Boolean = false) {
        val label = link.label.ifBlank { copy("profile.contacts") }
        val payload = contactPayload(link)
        val open = profileExportLink(payload)?.let { "<a class=\"button\" href=\"${it.html()}\" target=\"_blank\" rel=\"noopener noreferrer\">${htmlCopy("profile.export_open_link")} ↗</a>" }.orEmpty()
        val thumbnail = if (iconOnly) icons.icon(link) else "<strong>${label.html()}</strong><span>${link.value.html()}</span>"
        preview(if (iconOnly) copy("profile.export_contact_name", "name" to label) else label, thumbnail,
            profileArchiveQr(context, payload, ProfileQrStyle.INK) + "<p class=\"detail\">${link.value.html()}</p>$open")
    }
    return buildString {
        append("<!doctype html><html lang=\"$locale\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><meta name=\"referrer\" content=\"no-referrer\"><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src https://kigtts.invalid; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\"><title>$name · ${htmlCopy("profile.export_title")}</title><style>${theme.css()}$css</style>")
        append("<body><main id=\"home\"><header><div class=\"eyebrow\">KIGTTS · ${htmlCopy("profile.export_title")}</div>")
        if (!coverIsBackground) append("<h1 class=\"sr\">$name</h1>")
        append("<div class=\"cover-wrap\"><img class=\"cover\" src=\"cover.jpg\" alt=\"$name：${identity.bio.html()}\">")
        if (identity.contacts.isNotEmpty()) {
            append("<div class=\"cover-shortcuts\" aria-label=\"${htmlCopy("profile.export_contact_shortcuts")}\">")
            identity.contacts.take(5).forEach { contact(it, iconOnly = true) }
            if (identity.contacts.size > 5) {
                val more = identity.contacts.drop(5).joinToString("") { item ->
                    val value = item.value.html()
                    val display = "<strong>${item.label.ifBlank { copy("profile.contacts") }.html()}</strong><p>$value</p>"
                    profileExportLink(contactPayload(item))?.let { "<a class=\"button\" href=\"${it.html()}\">$display</a>" } ?: display
                }
                preview(copy("profile.export_more_contacts"), "<span aria-hidden=\"true\">···</span>", more)
            }
            append("</div>")
        }
        append("</div>")
        if (coverIsBackground) {
            append("<h1>$name</h1>")
            if (identity.bio.isNotBlank()) append("<p>${identity.bio.html()}</p>")
        }
        append("</header>")
        if (identity.tags.isNotEmpty()) append("<ul class=\"tags\">${identity.tags.joinToString("") { "<li>${it.html()}</li>" }}</ul>")
        val placements = packProfileTiles(identity.tiles.take(24))
        val rowCount = placements.maxOfOrNull { it.row + it.tile.rows } ?: 0
        val rows = (0 until rowCount).joinToString(" ") { row ->
            when (placements.firstOrNull { it.row == row && it.tile.kind.isSectionRow }?.tile?.kind) {
                ProfileTileKind.TITLE -> "minmax(36px,auto)"
                ProfileTileKind.DIVIDER -> "minmax(calc(var(--tile-unit) / 2),auto)"
                else -> "minmax(var(--tile-unit),auto)"
            }
        }
        append("<section class=\"tiles\" style=\"grid-template-rows:$rows\" aria-label=\"${htmlCopy("profile.export_tiles")}\">")
        placements.forEachIndexed { i, placement ->
            val tile = placement.tile
            val label = tile.title.html()
            val body = tile.text.ifBlank { tile.contact }
            val kind = tile.kind.name.lowercase()
            val width = tile.columns
            val height = tile.rows
            val qrPalette = if (tile.kind == ProfileTileKind.QR) profileQrPalette(tile.qrStyle, tile.qrKeepLight, theme.dark,
                Color.parseColor(theme.colors.getValue("primary")), Color.parseColor(theme.colors.getValue("surface-container-high"))) else null
            val qrLayout = when { qrPalette == null || label.isBlank() -> ""; width > height -> " qr-wide"; height > width -> " qr-tall"; else -> "" }
            val qrCss = qrPalette?.let { ";background:${cssColor(it.paper)};color:${cssColor(it.ink)}" }.orEmpty()
            val hasSymbol = tile.kind == ProfileTileKind.CONTACT || tile.kind == ProfileTileKind.LINK
            val iconOnly = hasSymbol && width == 1 && height == 1
            val symbolClass = if (iconOnly) " icon-only" else if (hasSymbol && height == 1) " icon-row" else ""
            fun symbol(svg: String, title: String, detail: String): String =
                "<span class=\"tile-symbol\" aria-hidden=\"true\">$svg</span>" + if (iconOnly) "" else
                    "<span class=\"tile-caption\"><strong>${title.html()}</strong><span>${detail.html()}</span></span>"
            append("<article class=\"tile $kind$qrLayout$symbolClass\" style=\"grid-column: ${placement.column + 1}/span $width;grid-row: ${placement.row + 1}/span $height$qrCss\">")
            when (tile.kind) {
                ProfileTileKind.TITLE -> append("<h2>$label</h2>")
                ProfileTileKind.DIVIDER -> append(profileDividerHtml(tile.title, tile.dividerStyle))
                ProfileTileKind.CONTACT -> {
                    val link = tile.resolveContact(identity.contacts)
                    val name = tile.title.ifBlank { link?.label?.ifBlank { copy("profile.contacts") } ?: copy("profile.contacts") }
                    val graphic = symbol(icons.icon(link ?: ContactLink("", "")), name, link?.value ?: body)
                    val payload = link?.let(::contactPayload).orEmpty()
                    if (link != null && payload.isNotBlank()) {
                        val open = profileExportLink(payload)?.let { "<a class=\"button\" href=\"${it.html()}\" target=\"_blank\" rel=\"noopener noreferrer\">${htmlCopy("profile.export_open_link")} ↗</a>" }.orEmpty()
                        preview(name, graphic, profileArchiveQr(context, payload, ProfileQrStyle.INK) + "<p class=\"detail\">${link.value.html()}</p>$open")
                    } else append(graphic)
                }
                ProfileTileKind.LINK -> {
                    val link = profileExportLink(body)
                    val name = tile.title.ifBlank { copy("profile.link_label") }
                    val graphic = symbol(icons.icon(SocialPlatform.LINK), name, body)
                    if (link != null) append("<a href=\"${link.html()}\" aria-label=\"${name.html()}\" target=\"_blank\" rel=\"noopener noreferrer\">$graphic</a>")
                    else append(graphic)
                }
                ProfileTileKind.IMAGE -> if (i in images) {
                    val alt = tile.title.ifBlank { copy("profile.export_image_preview") }
                    val image = "<img src=\"tile-$i.jpg\" alt=\"${alt.html()}\">"
                    preview(alt, image, image)
                } else append("<strong>${label.ifBlank { htmlCopy("profile.export_image") }}</strong>")
                ProfileTileKind.QR -> {
                    val qr = profileArchiveQr(context, tile.text, tile.qrStyle, qrPalette)
                    val heading = if (qrLayout.isNotBlank()) "<strong>$label</strong>" else ""
                    preview(tile.title.ifBlank { copy("profile.export_view_qr") }, qr + heading, qr + "<p class=\"detail\">${tile.text.html()}</p>")
                }
                else -> append("<strong>$label</strong><span>${body.html()}</span>")
            }
            append("</article>")
        }
        append("</section>")
        if (identity.contacts.isNotEmpty()) {
            append("<section class=\"contacts\" id=\"contacts\"><h2>${htmlCopy("profile.contacts")}</h2>")
            identity.contacts.forEach { link -> contact(link) }
            append("</section>")
        }
        append("<footer>${htmlCopy("profile.export_footer")}</footer></main></body></html>")
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

private fun cssColor(color: Int) = "#%06x".format(color and 0xffffff)

private fun profileArchiveQr(context: Context, value: String, style: ProfileQrStyle, palette: ProfileQrPalette? = null): String {
    val matrix = profileQrMatrix(value) ?: return "<p>${context.uiCopy("profile.export_qr_failed").html()}</p>"
    val edge = matrix.width
    val inkColor = palette?.let { cssColor(it.ink) } ?: "#111"
    val paperColor = palette?.let { cssColor(it.paper) } ?: "#fff"
    return buildString {
        append("<svg viewBox=\"0 0 $edge $edge\" role=\"img\" aria-label=\"${context.uiCopy("profile.qr").html()}\" style=\"color:$inkColor\"><rect width=\"$edge\" height=\"$edge\" fill=\"$paperColor\"/><path fill=\"currentColor\" d=\"")
        for (y in 0 until edge) for (x in 0 until edge) if (matrix[x, y] && (style != ProfileQrStyle.DOTS || !matrix.qrDotAllowed(x, y)))
            append("M$x ${y}h1v1h-1z")
        append("\"/>")
        if (style == ProfileQrStyle.DOTS) for (y in 0 until edge) for (x in 0 until edge)
            if (matrix[x, y] && matrix.qrDotAllowed(x, y)) append("<circle cx=\"${x}.5\" cy=\"${y}.5\" r=\".45\" fill=\"currentColor\"/>")
        append("</svg>")
    }
}
