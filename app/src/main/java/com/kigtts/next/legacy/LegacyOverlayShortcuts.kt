package com.kigtts.next.legacy

import android.content.ComponentName
import com.kigtts.next.overlay.OverlayShortcut
import org.json.JSONArray

/** The old DataStore records launcher component names and labels, with no intent payload. */
internal fun legacyOverlayShortcuts(preferences: Map<String, Any>): List<OverlayShortcut> {
    val text = (preferences["floating_overlay_shortcuts"] as? String)?.takeIf { it.length <= 256 * 1024 }
        ?: return emptyList()
    val entries = runCatching { checkBackupJsonDepth(text); JSONArray(text) }.getOrNull() ?: return emptyList()
    val pkgPattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    val classPattern = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
    return (0 until minOf(entries.length(), 100)).mapNotNull { index ->
        val item = entries.optJSONObject(index) ?: return@mapNotNull null
        val pkg = item.optString("packageName").trim()
        val saved = item.optString("className").trim()
        val type = when {
            saved.startsWith(".") -> pkg + saved
            '.' !in saved -> "$pkg.$saved"
            else -> saved
        }
        if (saved.isBlank() || pkg.length > 255 || type.length > 255 ||
            !pkgPattern.matches(pkg) || !classPattern.matches(type)) return@mapNotNull null
        val component = ComponentName(pkg, type).flattenToString()
        val label = item.optString("label").trim().take(120).ifBlank { type.substringAfterLast('.') }
        OverlayShortcut("app:$component", label, "apps", component = component, category = "应用")
    }.distinctBy(OverlayShortcut::id)
}
