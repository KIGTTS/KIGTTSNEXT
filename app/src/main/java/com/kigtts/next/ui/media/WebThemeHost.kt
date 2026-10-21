package com.kigtts.next.ui.media

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.kigtts.next.media.*

@Composable internal fun PublishWebTheme() {
    val c = MaterialTheme.colorScheme
    val colors = mapOf("primary" to c.primary, "on-primary" to c.onPrimary, "primary-container" to c.primaryContainer,
        "on-primary-container" to c.onPrimaryContainer, "secondary" to c.secondary, "on-secondary" to c.onSecondary,
        "surface" to c.surface, "surface-container" to c.surfaceContainer, "surface-container-high" to c.surfaceContainerHigh,
        "on-surface" to c.onSurface, "on-surface-variant" to c.onSurfaceVariant, "outline" to c.outline, "outline-variant" to c.outlineVariant, "error" to c.error)
    val theme = WebTheme(c.surface.luminance() < .5f, colors.mapValues { "#%06x".format(it.value.toArgb() and 0xffffff) }, com.kigtts.next.ui.components.LocalCornerRadius.current.value.toInt())
    SideEffect { WebThemeState.current = theme }
}
