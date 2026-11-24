package com.kigtts.next.legacy

import org.json.JSONObject

/** Matches the original Preferences DataStore export, including long values and string sets. */
internal fun decodeLegacyPreferences(payload: JSONObject): Map<String, Any> {
    require(payload.optInt("version", 1) == 1) { "不支持的旧版设置版本" }
    val entries = payload.getJSONArray("entries")
    require(entries.length() <= 4096) { "备份设置数量过多" }
    return buildMap {
        for (index in 0 until entries.length()) {
            val item = entries.getJSONObject(index)
            val name = item.getString("name")
            require(name.length in 1..160 && !containsKey(name)) { "备份包含无效或重复的设置" }
            val raw = item.get("value")
            val value = when (item.getString("type")) {
                "boolean" -> raw as Boolean
                "int" -> item.getInt("value").also { require(raw is Int || raw is Long && raw == it.toLong()) }
                "long" -> (raw as Number).toLong().also { require(raw is Long || raw is Int) }
                "float" -> (raw as Number).toFloat().also { require(it.isFinite()) }
                "double" -> (raw as Number).toDouble().also { require(it.isFinite()) }
                "string" -> raw as String
                "string_set" -> {
                    val list = item.getJSONArray("value")
                    require(list.length() <= 4096)
                    (0 until list.length()).map { list.get(it) as String }.toSet()
                }
                else -> raw
            }
            put(name, value)
        }
    }
}
