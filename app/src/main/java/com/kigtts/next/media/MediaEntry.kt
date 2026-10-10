package com.kigtts.next.media

import org.json.JSONObject
import org.json.JSONArray

enum class MediaKind(val label: String) { IMAGE("图片"), MOTION_PHOTO("实况照片"), VIDEO("视频"), HTML("HTML"), WEB("网页") }
enum class MediaScale(val label: String) { FIT("适应"), FILL("填充") }
enum class ThumbnailMode(val label: String) { AUTO("智能取景"), FIT("完整显示"), FOCUS("手动取景") }

data class MediaEntry(val id: String, val name: String, val kind: MediaKind, val primary: String,
    val mimeType: String = "", val video: String? = null, val pluginId: String? = null,
    val actions: List<String> = emptyList(), val network: Boolean = false, val created: Long = System.currentTimeMillis(), val appleLive: Boolean = false,
    val scale: MediaScale = MediaScale.FIT, val thumbnailMode: ThumbnailMode = ThumbnailMode.AUTO,
    val focusX: Double = .5, val focusY: Double = .5) {
    init { require(focusX in 0.0..1.0 && focusY in 0.0..1.0) }
    fun json() = JSONObject().put("id",id).put("name",name).put("kind",kind.name).put("primary",primary)
        .put("mime",mimeType).put("video",video).put("plugin",pluginId).put("actions",JSONArray(actions))
        .put("network",network).put("created",created).put("appleLive",appleLive).put("scale",scale.name)
        .put("thumbnailMode",thumbnailMode.name).put("focusX",focusX).put("focusY",focusY)
    companion object {
        fun read(json: JSONObject) = MediaEntry(json.getString("id"),json.getString("name"),MediaKind.valueOf(json.getString("kind")),
            json.getString("primary"),json.optString("mime"),json.optString("video").takeIf(String::isNotBlank),
            json.optString("plugin").takeIf(String::isNotBlank),json.optJSONArray("actions")?.let { (0 until it.length()).map(it::getString) } ?: emptyList(),
            json.optBoolean("network"),json.optLong("created"),json.optBoolean("appleLive"), MediaScale.entries.firstOrNull { it.name==json.optString("scale") } ?: MediaScale.FIT,
            ThumbnailMode.entries.firstOrNull { it.name == json.optString("thumbnailMode") } ?: ThumbnailMode.AUTO,
            json.optDouble("focusX", .5).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: .5,
            json.optDouble("focusY", .5).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: .5)
    }
}
