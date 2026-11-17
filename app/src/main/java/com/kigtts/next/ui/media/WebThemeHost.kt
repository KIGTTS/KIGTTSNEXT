package com.kigtts.next.ui.media

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import com.kigtts.next.media.*

@Composable internal fun PublishWebTheme(colors: ColorScheme, dark: Boolean, corner: Int) {
    val theme = remember(colors, dark, corner) { WebTheme(dark, webThemeColors(colors), corner) }
    SideEffect { WebThemeState.current = theme }
}
