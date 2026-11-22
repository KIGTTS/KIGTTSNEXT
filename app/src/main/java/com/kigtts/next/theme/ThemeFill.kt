package com.kigtts.next.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline

/** Cache the shape separately, then repaint its current role without restarting its motion. */
@Composable internal fun Modifier.themeFill(shape: Shape = RectangleShape, color: (ColorScheme) -> Color): Modifier {
    val paint = LocalThemePaint.current
    val fallback = MaterialTheme.colorScheme
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind { drawOutline(outline, color(paint?.palette?.colors ?: fallback)) }
    }
}
