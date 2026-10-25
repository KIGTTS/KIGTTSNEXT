package com.kigtts.next.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** A stronger cached blur remains registered to the page background through scrolling. */
@Composable internal fun AppearanceCardSurface(modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large, color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit) {
    val background = LocalSecondaryBackground.current
    Surface(modifier, shape = shape, color = if (background == null) color else Color.Transparent) {
        Box {
            if (background != null) AppearanceBackdrop(background, Modifier.matchParentSize(), blur = BackgroundBlur.CARD,
                overlay = color.copy(alpha = .72f))
            content()
        }
    }
}
