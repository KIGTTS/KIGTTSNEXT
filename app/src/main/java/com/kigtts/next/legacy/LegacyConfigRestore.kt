package com.kigtts.next.legacy

import android.content.Context
import android.net.Uri
import com.kigtts.next.backup.AppConfigPreferences
import com.kigtts.next.backup.ConfigValues
import com.kigtts.next.state.SubtitlePlaceholder
import com.kigtts.next.theme.AppearanceAssets
import java.io.File
import kotlin.math.abs

internal fun restoreLegacySettings(context: Context, content: LegacyContent): Boolean {
    val values = content.preferences
    if (values.isEmpty()) return false
    val appearance = buildMap<String, Any> {
        (values["theme_mode"] as? Int)?.let { mode ->
            listOf("SYSTEM", "LIGHT", "DARK").getOrNull(mode)?.let { put("mode", it) }
        }
        (values["theme_color_argb"] as? Int)?.let {
            put("seed", it.toLong() and 0xffffffffL)
            put("wallpaper", false)
            put("backgroundColor", false)
        }
        (values["quick_subtitle_cleared_placeholder"] as? String)?.let {
            put("subtitlePlaceholder", SubtitlePlaceholder.normalize(it))
        }
        legacyFont(context, content)?.let { (file, name, weight) ->
            put("font", file)
            put("fontName", name)
            put("fontWeight", weight)
        }
    }
    val mapped: ConfigValues = buildMap {
        if (appearance.isNotEmpty()) put("appearance", appearance)
        (values["haptic_feedback_enabled"] as? Boolean)?.let { put("interaction", mapOf("haptics" to it)) }
        (values["listening_mode_settings"] as? String)?.let { raw ->
            val size = runCatching { legacyJson(raw).optDouble("fontSizeSp").toFloat() }.getOrNull()
            if (size != null && size.isFinite()) put("caption-window", mapOf("panel-font-size" to size.coerceIn(10f, 64f)))
        }
        (values["playback_gain_percent"] as? Int)?.let {
            put("speech-audio-devices", mapOf("output-gain" to (it / 100f).coerceIn(0f, 10f)))
        }
    }
    if (mapped.isNotEmpty()) AppConfigPreferences(context).apply(mapped, replace = false)
    val shortcuts = legacyOverlayShortcuts(values)
    val enabled = values["floating_overlay_enabled"] as? Boolean
    val autoHide = values["floating_overlay_auto_dock"] as? Boolean
    val restoreOverlay = shortcuts.isNotEmpty() || enabled != null || autoHide != null
    if (restoreOverlay) com.kigtts.next.overlay.OverlayStore.get(context).update { current ->
        current.copy(enabled = enabled ?: current.enabled, autoHide = autoHide ?: current.autoHide,
            shortcuts = current.shortcuts + shortcuts)
    }
    return mapped.isNotEmpty() || restoreOverlay
}

private fun legacyFont(context: Context, content: LegacyContent): Triple<String, String, Int>? {
    val id = (content.preferences["app_font_id"] as? String)?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,96}")) }
        ?: return null
    if (id in setOf(".", "..", "system")) return null
    val root = content.resourceRoot ?: return null
    val directory = listOf("font", "fonts").asSequence().map { File(root, "$it/$id") }
        .firstOrNull { it.isDirectory && it.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) } ?: return null
    val weight = (content.preferences["app_font_weight"] as? Int ?: 400).coerceIn(1, 1000)
    return runCatching {
        val metadata = legacyJson(File(directory, "font.json").readText(Charsets.UTF_8))
        val weights = metadata.optJSONArray("fontFiles")
        require(weights == null || weights.length() <= 128)
        val name = if (metadata.optInt("weightMin") > 0 || weights == null) metadata.optString("fontFileName") else
            (0 until weights.length()).mapNotNull(weights::optJSONObject).minByOrNull {
                abs(it.optInt("weight", 400).coerceIn(1, 1000) - weight)
            }?.optString("fileName").orEmpty().ifEmpty { metadata.optString("fontFileName") }
        require(name.isNotEmpty() && name.none { it in "/\\:" } && name !in setOf(".", ".."))
        val source = File(directory, name)
        val key = AppearanceAssets(context).importFont(Uri.fromFile(source))
        Triple(key, metadata.optString("displayName").take(120).ifBlank { id }, weight)
    }.getOrNull()
}
