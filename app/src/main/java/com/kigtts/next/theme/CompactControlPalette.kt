package com.kigtts.next.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

data class CompactControlPalette(val container: Color, val content: Color, val plainContent: Color)
val LocalCompactControlPalette = compositionLocalOf<CompactControlPalette?> { null }
val LocalUseCompactControlPalette = compositionLocalOf { false }

/** Small light-mode actions use native medium-contrast roles without changing the page palette. */
fun compactControlPalette(settings: ThemeSettings, wallpaper: ColorScheme? = null,
    dark: Boolean = settings.mode.usesDark(true), base: ColorScheme = themeColors(settings, wallpaper, dark)): CompactControlPalette {
    val scheme = if (!dark && settings.contrast == ContrastLevel.DEFAULT)
        themeColors(settings.copy(contrast = ContrastLevel.MEDIUM), wallpaper, isDark = false) else base
    return CompactControlPalette(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.onSurface)
}

@Composable
fun animatedCompactControlPalette(target: CompactControlPalette): CompactControlPalette {
    @Composable fun color(value: Color, role: String) =
        animateColorAsState(value, tween(450, easing = FastOutSlowInEasing), label = "compact-" + role).value
    return CompactControlPalette(color(target.container, "container"), color(target.content, "content"), color(target.plainContent, "plain-content"))
}
