package com.kigtts.next.identity

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ProfileTileKind { TEXT, STATUS, CONTACT, LINK, TITLE, IMAGE, QR }

enum class ProfileQrStyle(val label: String) { THEME("主题色"), INK("黑白"), DOTS("圆点") }

internal fun ProfileTileKind.maxTileRows(): Int = if (this == ProfileTileKind.QR) 4 else 3

data class ProfileTile(val id: String = UUID.randomUUID().toString(), val kind: ProfileTileKind = ProfileTileKind.TEXT,
    val title: String = "", val text: String = "", val contact: String = "", val columns: Int = 2, val rows: Int = 1,
    val image: String = "", val social: ContactLink? = null, val qrStyle: ProfileQrStyle = ProfileQrStyle.THEME) {
    fun json() = JSONObject().put("id", id).put("kind", kind.name).put("title", title).put("text", text)
        .put("contact", contact).put("columns", columns).put("rows", rows).put("image", image)
        .put("social", social?.let { JSONObject().put("label", it.label).put("value", it.value).put("qrContent", it.qrContent) })
        .apply { if (kind == ProfileTileKind.QR) put("qrStyle", qrStyle.name) }
    companion object {
        fun from(json: JSONObject): ProfileTile {
            val kind = runCatching { ProfileTileKind.valueOf(json.optString("kind")) }.getOrDefault(ProfileTileKind.TEXT)
            return ProfileTile(json.optString("id").take(80).ifBlank { UUID.randomUUID().toString() }, kind,
            json.optString("title").take(40), json.optString("text").take(2048), json.optString("contact").take(2048),
            json.optInt("columns", 2).coerceIn(1, 4), json.optInt("rows", 1).coerceIn(1, kind.maxTileRows()),
            json.optString("image").take(80).takeIf { it.matches(Regex("[A-Za-z0-9_.-]{0,80}")) }.orEmpty(),
            profileTileSocial(json.optJSONObject("social")),
            ProfileQrStyle.entries.firstOrNull { it.name == json.optString("qrStyle") } ?: ProfileQrStyle.THEME)
        }
    }
}

internal val ProfileTile.isTitleOnlyText: Boolean
    get() = kind == ProfileTileKind.TEXT && title.isNotBlank() && text.isBlank()

internal fun profileTags(json: JSONObject): List<String> = json.optJSONArray("tags")?.let { list ->
    (0 until minOf(list.length(), 12)).mapNotNull { i -> (list.opt(i) as? String)?.trim()?.take(20)?.takeIf(String::isNotBlank) }.distinct()
}.orEmpty()

internal fun profileTiles(json: JSONObject): List<ProfileTile> = json.optJSONArray("tiles")?.let { list ->
    (0 until minOf(list.length(), 24)).mapNotNull { list.optJSONObject(it)?.let(ProfileTile::from) }.distinctBy { it.id }
}.orEmpty()

internal fun profileTileJson(tiles: List<ProfileTile>) = JSONArray(tiles.map(ProfileTile::json))
internal fun profileTileMediaId(id: String) = UUID.nameUUIDFromBytes(("profile-tile/" + id).toByteArray(Charsets.UTF_8)).toString()

/** Shared four-column, first-free-space layout. Resizing never overlaps another tile. */
internal data class ProfileTilePlacement(val tile: ProfileTile, val column: Int, val row: Int)
internal fun packProfileTiles(tiles: List<ProfileTile>): List<ProfileTilePlacement> {
    val occupied = mutableSetOf<Pair<Int, Int>>()
    var sectionStart = 0
    return tiles.map { original ->
        val tile = if (original.kind == ProfileTileKind.TITLE) original.copy(columns = 4, rows = 1)
            else original.copy(columns = original.columns.coerceIn(1, 4), rows = original.rows.coerceIn(1, original.kind.maxTileRows()))
        var row = if (tile.kind == ProfileTileKind.TITLE) maxOf(sectionStart, (occupied.maxOfOrNull { it.second } ?: -1) + 1) else sectionStart
        var column = 0
        while ((column until column + tile.columns).any { x -> (row until row + tile.rows).any { y -> x to y in occupied } }) {
            column++
            if (column + tile.columns > 4) { column = 0; row++ }
        }
        for (x in column until column + tile.columns) for (y in row until row + tile.rows) occupied += x to y
        if (tile.kind == ProfileTileKind.TITLE) sectionStart = row + 1
        ProfileTilePlacement(tile, column, row)
    }
}
