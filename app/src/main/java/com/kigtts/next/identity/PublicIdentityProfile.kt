package com.kigtts.next.identity

import org.json.JSONObject

/** A bounded public view shared by received cards, nearby details and the local web page. */
internal data class PublicIdentityProfile(val name: String, val bio: String, val avatar: String?,
    val avatarFrame: String, val contacts: List<ContactLink>, val tags: List<String>, val tiles: List<ProfileTile>) {
    fun json() = JSONObject().put("nickname", name).put("bio", bio).put("avatar", avatar ?: "").put("avatarFrame", avatarFrame)
        .put("contacts", org.json.JSONArray(contacts.map { JSONObject().put("label", it.label).put("value", it.value).put("qrContent", it.qrContent) }))
        .put("tags", org.json.JSONArray(tags)).put("tiles", profileTileJson(tiles))
    companion object {
        fun from(json: JSONObject): PublicIdentityProfile {
            val links = json.optJSONArray("contacts")
            return PublicIdentityProfile(json.optString("nickname").take(40).ifBlank { "KIGTTS 用户" },
                json.optString("bio").take(240), json.optString("avatar").takeIf { it.length in 1..65535 },
                json.optString("avatarFrame", "circle"), (0 until minOf(links?.length() ?: 0, 8)).mapNotNull { index ->
                    links?.optJSONObject(index)?.let { entry -> ContactLink(entry.optString("label").take(24),
                        entry.optString("value").take(2048), entry.optString("qrContent").take(2048).takeIf(String::isNotBlank)) }
                        ?.takeIf { it.value.isNotBlank() }
                }, profileTags(json), profileTiles(json))
        }
    }
}
