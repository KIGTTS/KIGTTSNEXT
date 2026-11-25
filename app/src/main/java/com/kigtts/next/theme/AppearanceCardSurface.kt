package com.kigtts.next.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag

/** A stronger cached blur remains registered to the page background through scrolling. */
@Composable internal fun AppearanceCardSurface(modifier: Modifier = Modifier,
    shape: Shape? = null, color: Color = Color.Unspecified,
    content: @Composable () -> Unit) {
    val background = LocalSecondaryBackground.current
    val density = LocalDensity.current
    val large = MaterialTheme.shapes.large
    val outer = if (shape == null && large is RoundedCornerShape && large.topStart == large.topEnd &&
        large.topStart == large.bottomStart && large.topStart == large.bottomEnd) {
        val corner = animateDpAsState(with(density) { large.topStart.toPx(Size.Zero, density).toDp() },
            tween(220, easing = FastOutSlowInEasing), label = "settings-card-corner")
        modifier.graphicsLayer { this.shape = RoundedCornerShape(corner.value); clip = true }
    } else modifier
    val container = if (color == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainerHigh else color
    val fill: (ColorScheme) -> Color = { if (color == Color.Unspecified) it.surfaceContainerHigh else color }
    Surface(outer, shape = shape ?: if (outer === modifier) large else RectangleShape,
        color = Color.Transparent, contentColor = if (background == null) contentColorFor(container) else LocalContentColor.current) {
        Box(if (background == null) Modifier.themeFill(color = fill) else Modifier) {
            if (background != null) AppearanceBackdrop(background,
                Modifier.matchParentSize().testTag("secondary-card-backdrop"), blur = BackgroundBlur.CARD,
                overlayColor = { fill(it).copy(alpha = .72f) })
            content()
        }
    }
}
