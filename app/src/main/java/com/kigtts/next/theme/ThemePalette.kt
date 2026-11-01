package com.kigtts.next.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ThemePaletteKey(val seed: Long, val contrast: ContrastLevel,
    val deeper: Boolean, val dark: Boolean, val wallpaper: ColorScheme?)

internal data class ThemePalette(val colors: ColorScheme, val controls: CompactControlPalette)

/** Only color inputs are keys: layout, background opacity and font changes reuse the palette. */
internal object ThemePalettes {
    private val cache = object : LinkedHashMap<ThemePaletteKey, ThemePalette>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ThemePaletteKey, ThemePalette>) = size > 16
    }
    @Synchronized fun cached(key: ThemePaletteKey): ThemePalette? = cache[key]
    fun resolve(key: ThemePaletteKey): ThemePalette {
        cached(key)?.let { return it }
        val settings = ThemeSettings(seed = key.seed, contrast = key.contrast, deeperDark = key.deeper)
        val colors = themeColors(settings, key.wallpaper, key.dark)
        val result = ThemePalette(colors, compactControlPalette(settings, key.wallpaper, key.dark, colors))
        return synchronized(this) { cache.getOrPut(key) { result } }
    }
}

@Composable
internal fun rememberThemePalette(key: ThemePaletteKey): ThemePalette {
    // Resolve the initial theme before drawing, so launch never flashes a fallback palette.
    var palette by remember { mutableStateOf(ThemePalettes.resolve(key)) }
    LaunchedEffect(key) {
        palette = ThemePalettes.cached(key) ?: withContext(Dispatchers.Default) { ThemePalettes.resolve(key) }
    }
    return palette
}
