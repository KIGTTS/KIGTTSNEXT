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

enum class ThemeColorSource(val label: String) { WALLPAPER("壁纸"), BACKGROUND("背景图"), CUSTOM("自定义") }

data class ThemeSettings(
    val seed: Long = 0xFF00696E,
    val contrast: ContrastLevel = ContrastLevel.DEFAULT,
    val deeperDark: Boolean = false,
    val cornerLevel: CornerLevel = CornerLevel.LARGE,
    val wallpaperEnabled: Boolean = false,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val background: String? = null,
    val backgroundDim: Float = .35f,
    val font: String? = null,
    val fontName: String = "系统默认",
    val backgroundColorEnabled: Boolean = false,
    val backgroundSeed: Long? = null,
    val askBackgroundColor: Boolean = true,
    val secondaryPageBackground: Boolean = true,
    val fontWeight: Int = 400,
) {
    val cornerRadius: Float get() = cornerLevel.radius
    val hex: String get() = "#%06X".format(seed and 0xFFFFFF)
    val colorSource get() = when {
        wallpaperEnabled -> ThemeColorSource.WALLPAPER
        backgroundColorEnabled -> ThemeColorSource.BACKGROUND
        else -> ThemeColorSource.CUSTOM
    }
    val effectiveSeed get() = if (backgroundColorEnabled && background != null) backgroundSeed ?: seed else seed
    fun withColorSource(source: ThemeColorSource) = copy(wallpaperEnabled = source == ThemeColorSource.WALLPAPER,
        backgroundColorEnabled = source == ThemeColorSource.BACKGROUND)
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
        background = preferences.getString("background", null),
        backgroundDim = preferences.getFloat("backgroundDim", .35f).coerceIn(0f, 1f),
        font = preferences.getString("font", null),
        fontName = preferences.getString("fontName", "系统默认") ?: "系统默认",
        backgroundColorEnabled = preferences.getBoolean("backgroundColor", false),
        backgroundSeed = if (preferences.contains("backgroundSeed")) preferences.getLong("backgroundSeed", 0) else null,
        askBackgroundColor = preferences.getBoolean("askBackgroundColor", true),
        secondaryPageBackground = preferences.getBoolean("secondaryPageBackground", true),
        fontWeight = preferences.getInt("fontWeight", 400).coerceIn(1, 1000),
    )
    fun save(settings: ThemeSettings) {
        preferences.edit().putLong("seed", settings.seed).putFloat("contrast", settings.contrast.value)
            .putBoolean("deeperDark", settings.deeperDark).putFloat("radius", settings.cornerRadius)
            .putBoolean("wallpaper", settings.wallpaperEnabled).putString("mode", settings.mode.name)
            .putString("background", settings.background).putFloat("backgroundDim", settings.backgroundDim.coerceIn(0f, 1f))
            .putBoolean("secondaryPageBackground", settings.secondaryPageBackground)
            .putString("font", settings.font).putString("fontName", settings.fontName)
            .putInt("fontWeight", settings.fontWeight.coerceIn(1, 1000))
            .putBoolean("backgroundColor", settings.backgroundColorEnabled)
            .putBoolean("askBackgroundColor", settings.askBackgroundColor).apply {
                if (settings.backgroundSeed == null) remove("backgroundSeed") else putLong("backgroundSeed", settings.backgroundSeed)
            }.apply()
    }
}
