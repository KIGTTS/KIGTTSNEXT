package com.kigtts.next.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** The panel owns this fraction, so a partial drag fades the icons in both directions. */
data class ToolIconBlend(val collapsed: ImageVector, val expanded: ImageVector, val fraction: Float)

@Composable
internal fun BlendedToolIcon(blend: ToolIconBlend, size: Dp) {
    val progress = blend.fraction.coerceIn(0f, 1f)
    Icon(blend.collapsed, null, Modifier.size(size).graphicsLayer { alpha = 1f - progress })
    Icon(blend.expanded, null, Modifier.size(size).graphicsLayer { alpha = progress })
}
