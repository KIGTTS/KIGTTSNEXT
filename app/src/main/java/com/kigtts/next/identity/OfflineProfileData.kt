package com.kigtts.next.identity

import org.json.JSONArray
import org.json.JSONObject

/** The website and the visible image are not used as input data. */
internal object OfflineProfileData {
    fun parse(raw: String): JSONObject? {
        var nesting = 0
        var quoted = false
        var escaped = false
        for (ch in raw) {
            if (quoted) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') quoted = false
            } else when (ch) {
                '"' -> quoted = true
                '{', '[' -> if (++nesting > 16) return null
                '}', ']' -> if (--nesting < 0) return null
            }
        }
        if (quoted || nesting != 0) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun snapshot(source: JSONObject): JSONObject? {
        val nickname = (source.opt("nickname") as? String)?.take(40)?.takeIf(String::isNotBlank) ?: return null
        fun JSONObject.copyStrings(keys: Set<String>) = JSONObject().also { result ->
            keys.forEach { key -> (opt(key) as? String)?.let { result.put(key, it) } }
        }
        val contactKeys = setOf("label", "value", "qrContent")
        val typed = source.copyStrings(setOf("bio", "identityId", "avatar", "avatarFrame")).put("nickname", nickname)
        typed.put("tags", JSONArray(profileTags(source)))
        typed.put("contacts", JSONArray(source.optJSONArray("contacts").objects(8)
            .map { it.copyStrings(contactKeys) }))
        typed.put("tiles", JSONArray(source.optJSONArray("tiles").objects(24).mapNotNull { item ->
            if (ProfileTileKind.entries.none { it.name == item.opt("kind") }) return@mapNotNull null
            item.copyStrings(setOf("id", "kind", "title", "text", "contact", "qrStyle", "dividerStyle")).also { tile ->
                listOf("columns", "rows", "columnHint", "rowHint").forEach { key -> (item.opt(key) as? Int)?.let { tile.put(key, it) } }
                (item.opt("qrKeepLight") as? Boolean)?.let { tile.put("qrKeepLight", it) }
                item.optJSONObject("social")?.let { tile.put("social", it.copyStrings(contactKeys)) }
            }
        }))
        return PublicIdentityProfile.from(typed).json().apply {
            val identity = typed.optString("identityId").take(128)
            if (identity.isNotBlank()) put("identityId", identity)
            copyProfileCoverMetadata(source, this)
        }
    }

    private fun JSONArray?.objects(max: Int): List<JSONObject> = if (this == null) emptyList()
        else (0 until minOf(length(), max)).mapNotNull(::optJSONObject)
}
