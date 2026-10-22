package com.kigtts.next.ui.media

import org.json.JSONObject

/** JSON is passed as data, never interpolated into HTML or a quoted JavaScript string. */
internal object SubtitleRendererProtocol {
    fun update(text: String, placeholder: String, fontSizePercent: Float, settings: String = "{}",
        safeArea: MediaSafeArea = MediaSafeArea(), density: Float = 1f): String {
        val scale = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val payload = JSONObject().put("text", text).put("placeholder", placeholder)
            .put("fontSizePercent", fontSizePercent.takeIf(Float::isFinite)?.coerceIn(1f, 100f) ?: 28f)
            .put("settings", runCatching { JSONObject(settings) }.getOrElse { JSONObject() })
            .put("safeArea", JSONObject().put("top", safeArea.top.coerceAtLeast(0) / scale)
                .put("right", safeArea.right.coerceAtLeast(0) / scale).put("bottom", safeArea.bottom.coerceAtLeast(0) / scale)
                .put("left", safeArea.left.coerceAtLeast(0) / scale))
        val json = payload.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
        return "(()=>{const r=window.KigttsSubtitle;if(!r||typeof r.update!=='function')return false;r.update($json);return true;})()"
    }
    fun activity(active: Boolean) = "(()=>{const r=window.KigttsSubtitle;if(r&&typeof r.${if (active) "resume" else "pause"}==='function')r.${if (active) "resume" else "pause"}();})()"
}
