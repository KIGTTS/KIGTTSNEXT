package com.kigtts.next.ui.media

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.luminance
import com.kigtts.next.media.*

@Composable internal fun PublishWebTheme() {
    val c = MaterialTheme.colorScheme
    val theme = WebTheme(c.surface.luminance() < .5f, webThemeColors(c), com.kigtts.next.ui.components.LocalCornerRadius.current.value.toInt())
    SideEffect { WebThemeState.current = theme }
}
