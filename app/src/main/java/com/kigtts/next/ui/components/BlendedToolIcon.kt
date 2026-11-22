package com.kigtts.next.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.kigtts.next.theme.ThemeIcon

/** The panel owns this fraction, so a partial drag fades the icons in both directions. */
data class ToolIconBlend(val collapsed: ImageVector, val expanded: ImageVector, val fraction: Float)

@Composable
internal fun BlendedToolIcon(blend: ToolIconBlend, size: Dp) {
    val progress = blend.fraction.coerceIn(0f, 1f)
    val icon = Modifier.size(size)
    when (progress) {
        0f -> ThemeIcon(blend.collapsed, null, icon)
        1f -> ThemeIcon(blend.expanded, null, icon)
        else -> {
            ThemeIcon(blend.collapsed, null, icon.graphicsLayer { alpha = 1f - progress })
            ThemeIcon(blend.expanded, null, icon.graphicsLayer { alpha = progress })
        }
    }
}
