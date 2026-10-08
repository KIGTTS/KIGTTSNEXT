package com.kigtts.next.theme

import android.content.Context

enum class ContrastLevel(val label: String, val value: Float) {
    DEFAULT("默认", 0f), MEDIUM("中", .5f), HIGH("高", 1f);
    companion object {
        fun fromLegacy(value: Float) = entries.minBy { kotlin.math.abs(it.value - value) }
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("亮色"), DARK("深色");
    fun usesDark(systemDark: Boolean) = when (this) { SYSTEM -> systemDark; LIGHT -> false; DARK -> true }
}

data class ThemeSettings(
    val seed: Long = 0xFF00696E,
    val contrast: ContrastLevel = ContrastLevel.DEFAULT,
    val deeperDark: Boolean = false,
    val cornerLevel: CornerLevel = CornerLevel.LARGE,
    val wallpaperEnabled: Boolean = false,
    val mode: ThemeMode = ThemeMode.SYSTEM,
) {
    val cornerRadius: Float get() = cornerLevel.radius
    val hex: String get() = "#%06X".format(seed and 0xFFFFFF)
}

class ThemeSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun load() = ThemeSettings(
        seed = preferences.getLong("seed", 0xFF00696E),
        contrast = ContrastLevel.fromLegacy(preferences.getFloat("contrast", 0f)),
        deeperDark = preferences.getBoolean("deeperDark", false),
        cornerLevel = CornerLevel.fromLegacy(preferences.getFloat("radius", CornerLevel.LARGE.radius)),
        wallpaperEnabled = preferences.getBoolean("wallpaper", false),
        mode = ThemeMode.entries.firstOrNull { it.name == preferences.getString("mode", null) } ?: ThemeMode.SYSTEM,
    )
    fun save(settings: ThemeSettings) {
        preferences.edit().putLong("seed", settings.seed).putFloat("contrast", settings.contrast.value)
            .putBoolean("deeperDark", settings.deeperDark).putFloat("radius", settings.cornerRadius)
            .putBoolean("wallpaper", settings.wallpaperEnabled).putString("mode", settings.mode.name).apply()
    }
}
