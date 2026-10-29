package com.kigtts.next.theme

import android.content.Context
import org.json.JSONObject
import java.io.File

data class InstalledFont(val key: String, val name: String)

class InstalledFonts(context: Context) {
    private val preferences = context.getSharedPreferences("appearance-fonts", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "appearance-assets")
    fun remember(key: String, name: String) {
        require(key.matches(Regex("[a-f0-9]{64}\\.(ttf|otf)")))
        preferences.edit().putString(key, name.take(120)).apply()
    }
    fun remove(key: String) {
        require(key.matches(Regex("[a-f0-9]{64}\\.(ttf|otf)")))
        val file = File(root, key)
        check(!file.exists() || file.delete()) { "字体未能删除，请重试" }
        preferences.edit().remove(key).apply()
    }
    fun list(current: ThemeSettings): List<InstalledFont> {
        if (current.font != null && current.fontName.isNotBlank()) remember(current.font, current.fontName)
        return root.listFiles().orEmpty().filter { it.isFile && it.name.matches(Regex("[a-f0-9]{64}\\.(ttf|otf)")) }
            .map { InstalledFont(it.name, preferences.getString(it.name, "导入的字体") ?: "导入的字体") }
            .sortedWith(compareBy<InstalledFont> { it.name }.thenBy { it.key })
    }
}
