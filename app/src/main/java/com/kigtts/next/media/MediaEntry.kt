package com.kigtts.next.media

import org.json.JSONObject
import org.json.JSONArray

enum class MediaKind(val label: String) { IMAGE("图片"), MOTION_PHOTO("实况照片"), VIDEO("视频"), HTML("HTML"), WEB("网页") }
enum class MediaScale(val label: String) { FIT("适应"), FILL("填充") }

data class MediaEntry(val id: String, val name: String, val kind: MediaKind, val primary: String,
    val mimeType: String = "", val video: String? = null, val pluginId: String? = null,
    val actions: List<String> = emptyList(), val network: Boolean = false, val created: Long = System.currentTimeMillis(), val appleLive: Boolean = false,
    val scale: MediaScale = MediaScale.FIT) {
    fun json() = JSONObject().put("id",id).put("name",name).put("kind",kind.name).put("primary",primary)
        .put("mime",mimeType).put("video",video).put("plugin",pluginId).put("actions",JSONArray(actions))
        .put("network",network).put("created",created).put("appleLive",appleLive).put("scale",scale.name)
    companion object {
        fun read(json: JSONObject) = MediaEntry(json.getString("id"),json.getString("name"),MediaKind.valueOf(json.getString("kind")),
            json.getString("primary"),json.optString("mime"),json.optString("video").takeIf(String::isNotBlank),
            json.optString("plugin").takeIf(String::isNotBlank),json.optJSONArray("actions")?.let { (0 until it.length()).map(it::getString) } ?: emptyList(),
            json.optBoolean("network"),json.optLong("created"),json.optBoolean("appleLive"), MediaScale.entries.firstOrNull { it.name==json.optString("scale") } ?: MediaScale.FIT)
    }
}
