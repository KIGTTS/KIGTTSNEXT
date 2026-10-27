package com.kigtts.next.sharing

import com.kigtts.next.identity.*

internal fun faceWebTiles(person: PublicIdentityProfile, path: String, media: List<FaceAsset>, icon: (ContactLink) -> String): String {
    val tags = person.tags.joinToString("") { "<span>${faceHtml(it)}</span>" }
    val placements = packProfileTiles(person.tiles)
    val rowCount = placements.maxOfOrNull { it.row + it.tile.rows } ?: 0
    val rows = (0 until rowCount).joinToString(" ") { row -> if (placements.any { it.row == row && it.tile.kind == ProfileTileKind.TITLE }) "36px" else "var(--tile-unit)" }
    val tiles = placements.joinToString("") { (tile, column, row) ->
        val style = "grid-column:${column + 1}/span ${tile.columns};grid-row:${row + 1}/span ${tile.rows}"
        val link = person.contacts.firstOrNull { it.value == tile.contact }
        val label = faceHtml(tile.title.ifBlank { link?.label.orEmpty() })
        val iconOnly = tile.columns == 1 && tile.rows == 1 && tile.kind in setOf(ProfileTileKind.LINK, ProfileTileKind.CONTACT)
        val tileIcon = when (tile.kind) {
            ProfileTileKind.LINK -> icon(ContactLink("链接", ""))
            ProfileTileKind.CONTACT -> icon(link ?: ContactLink("", ""))
            else -> ""
        }
        val content = if (iconOnly) tileIcon else when (tile.kind) {
            ProfileTileKind.IMAGE -> media.firstOrNull { it.photo == profileTileMediaId(tile.id) }?.let { "<img loading=\"lazy\" src=\"${path}file/${it.id}\" alt=\"$label\">" }
                ?: "<span class=\"tile-placeholder\">图片</span>"
            ProfileTileKind.CONTACT -> "$tileIcon<strong>$label</strong><small>${faceHtml(link?.value ?: "联系方式已移除")}</small>"
            ProfileTileKind.LINK -> "$tileIcon<strong>$label</strong><span>${faceHtml(tile.text)}</span>"
            ProfileTileKind.TITLE -> "<strong>$label</strong>"
            else -> "<strong>$label</strong><span>${faceHtml(tile.text)}</span>"
        }
        val compact = (if (tile.rows == 1 && tile.kind !in setOf(ProfileTileKind.IMAGE, ProfileTileKind.TITLE)) " tile-compact" else "") +
            if (iconOnly) " tile-icon-only" else ""
        val accessible = if (iconOnly) " aria-label=\"${label.ifBlank { "链接" }}\"" else ""
        when {
            tile.kind == ProfileTileKind.CONTACT && link != null -> "<button class=\"profile-tile$compact\"$accessible style=\"$style\" data-contact=\"contact-${person.contacts.indexOf(link)}\">$content</button>"
            tile.kind == ProfileTileKind.LINK && faceContactUrl(ContactLink("链接", tile.text))?.startsWith("http") == true ->
                "<a class=\"profile-tile$compact\"$accessible style=\"$style\" href=\"${faceHtml(tile.text)}\" target=\"_blank\" rel=\"noopener noreferrer\">$content</a>"
            else -> "<section class=\"profile-tile$compact${if (tile.kind == ProfileTileKind.TITLE) " tile-title" else ""}${if (tile.kind == ProfileTileKind.IMAGE) " tile-image" else ""}\"$accessible style=\"$style\">$content</section>"
        }
    }
    return "<div class=\"profile-tags\">$tags</div><div class=\"tiles-host\"><div class=\"profile-tiles\" style=\"grid-template-rows:$rows\">$tiles</div></div>"
}

internal const val faceTileStyles = """
.profile-tile.tile-icon-only{display:flex;align-items:center;justify-content:center;padding:8px}.profile-tile.tile-icon-only>svg{width:24px;height:24px}
.profile-tags{display:flex;flex-wrap:wrap;gap:8px;text-align:left;margin:16px 0}.profile-tags>span{padding:6px 12px;border-radius:20px;background:var(--md-sys-color-secondary-container);color:var(--md-sys-color-on-secondary-container);font-size:14px}.tiles-host{container-type:inline-size}.profile-tiles{--tile-unit:calc((100cqw - 36px)/4);display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:16px 0;text-align:left}.profile-tile{display:flex;flex-direction:column;justify-content:center;gap:6px;padding:12px 16px;min-width:0;overflow:hidden;border:0;border-radius:var(--kigtts-corner);background:var(--md-sys-color-surface-container-high);color:var(--md-sys-color-on-surface);text-decoration:none;text-align:left;overflow-wrap:anywhere}.profile-tile strong{font-size:16px}.profile-tile span,.profile-tile small{font-size:14px;color:var(--md-sys-color-on-surface-variant);white-space:pre-wrap;overflow:hidden}.profile-tile svg{width:28px;height:28px;fill:currentColor;flex:none}.tile-title{padding:4px;background:transparent;white-space:nowrap;display:block;line-height:28px;text-overflow:ellipsis}.tile-image{padding:0}.tile-image img{width:100%;height:100%;object-fit:cover}.tile-placeholder{margin:auto}.tile-compact{gap:2px;padding:8px 12px}.tile-compact strong,.tile-compact span,.tile-compact small{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0}.tile-compact strong{font-size:14px}.tile-compact span,.tile-compact small{font-size:12px}.tile-compact:has(>svg){display:grid;grid-template-columns:24px minmax(0,1fr);grid-template-rows:1fr 1fr;align-content:center;gap:0 8px}.tile-compact>svg{grid-column:1;grid-row:1/3;width:24px;height:24px;align-self:center}.tile-compact:has(>svg)>strong,.tile-compact:has(>svg)>small{grid-column:2}
"""
