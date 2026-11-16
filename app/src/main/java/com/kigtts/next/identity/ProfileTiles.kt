package com.kigtts.next.identity

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ProfileTileKind { TEXT, STATUS, CONTACT, LINK, TITLE, IMAGE, QR, DIVIDER }

enum class ProfileQrStyle(val label: String) { THEME("主题色"), INK("黑白"), DOTS("圆点") }
enum class ProfileDividerStyle(val label: String) { LINE("直线"), WAVE("波浪线") }

internal fun ProfileTileKind.maxTileRows(): Int = if (this == ProfileTileKind.QR) 4 else 3
internal val ProfileTileKind.isSectionRow get() = this == ProfileTileKind.TITLE || this == ProfileTileKind.DIVIDER

data class ProfileTile(val id: String = UUID.randomUUID().toString(), val kind: ProfileTileKind = ProfileTileKind.TEXT,
    val title: String = "", val text: String = "", val contact: String = "", val columns: Int = 2, val rows: Int = 1,
    val image: String = "", val social: ContactLink? = null, val qrStyle: ProfileQrStyle = ProfileQrStyle.THEME,
    val qrKeepLight: Boolean = false, val dividerStyle: ProfileDividerStyle = ProfileDividerStyle.LINE,
    val columnHint: Int? = null, val rowHint: Int? = null) {
    fun json() = JSONObject().put("id", id).put("kind", kind.name).put("title", title).put("text", text)
        .put("contact", contact).put("columns", columns).put("rows", rows).put("image", image)
        .put("social", social?.let { JSONObject().put("label", it.label).put("value", it.value).put("qrContent", it.qrContent) })
        .apply { if (kind == ProfileTileKind.QR) { put("qrStyle", qrStyle.name); put("qrKeepLight", qrKeepLight) } }
        .apply {
            if (kind == ProfileTileKind.DIVIDER) put("dividerStyle", dividerStyle.name)
            if (!kind.isSectionRow) {
                columnHint?.let { put("columnHint", it) }
                rowHint?.let { put("rowHint", it) }
            }
        }
    companion object {
        fun from(json: JSONObject): ProfileTile {
            val kind = runCatching { ProfileTileKind.valueOf(json.optString("kind")) }.getOrDefault(ProfileTileKind.TEXT)
            return ProfileTile(json.optString("id").take(80).ifBlank { UUID.randomUUID().toString() }, kind,
            json.optString("title").take(40), json.optString("text").take(2048), json.optString("contact").take(2048),
            json.optInt("columns", 2).coerceIn(1, 4), json.optInt("rows", 1).coerceIn(1, kind.maxTileRows()),
            json.optString("image").take(80).takeIf { it.matches(Regex("[A-Za-z0-9_.-]{0,80}")) }.orEmpty(),
            profileTileSocial(json.optJSONObject("social")),
            ProfileQrStyle.entries.firstOrNull { it.name == json.optString("qrStyle") } ?: ProfileQrStyle.THEME,
            json.optBoolean("qrKeepLight", false),
            ProfileDividerStyle.entries.firstOrNull { it.name == json.optString("dividerStyle") } ?: ProfileDividerStyle.LINE,
            json.optInt("columnHint", -1).takeIf { it in 0..3 }, json.optInt("rowHint", -1).takeIf { it in 0..96 })
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
    val output = mutableListOf<ProfileTilePlacement>()
    var offset = 0
    val section = mutableListOf<ProfileTile>()
    fun flush() {
        val bits = mutableMapOf<Int, Int>()
        val placed = mutableMapOf<String, ProfileTilePlacement>()
        fun fits(tile: ProfileTile, column: Int, row: Int): Boolean {
            val mask = ((1 shl tile.columns) - 1) shl column
            return (row until row + tile.rows).none { (bits[it] ?: 0) and mask != 0 }
        }
        fun put(tile: ProfileTile, column: Int, row: Int) {
            val mask = ((1 shl tile.columns) - 1) shl column
            for (y in row until row + tile.rows) bits[y] = (bits[y] ?: 0) or mask
            placed[tile.id] = ProfileTilePlacement(tile, column, row)
        }
        section.filter { it.columnHint != null && it.rowHint != null }.forEach { tile ->
            val column = tile.columnHint!!.coerceIn(0, 4 - tile.columns)
            val row = tile.rowHint!!.coerceIn(0, 96)
            if (fits(tile, column, row)) put(tile, column, row)
        }
        section.filterNot { it.id in placed }.forEach { tile ->
            var row = 0
            var column = 0
            while (!fits(tile, column, row)) {
                column++
                if (column + tile.columns > 4) { column = 0; row++ }
            }
            put(tile, column, row)
        }
        val compact = bits.keys.sorted().withIndex().associate { (index, row) -> row to index }
        section.forEach { tile ->
            val position = placed.getValue(tile.id)
            output += position.copy(row = offset + compact.getValue(position.row))
        }
        offset += compact.size
        section.clear()
    }
    for (tile in tiles) if (tile.kind.isSectionRow) {
        flush()
        output += ProfileTilePlacement(tile.copy(columns = 4, rows = 1), 0, offset++)
    } else section += tile.copy(columns = tile.columns.coerceIn(1, 4), rows = tile.rows.coerceIn(1, tile.kind.maxTileRows()))
    flush()
    return output
}
