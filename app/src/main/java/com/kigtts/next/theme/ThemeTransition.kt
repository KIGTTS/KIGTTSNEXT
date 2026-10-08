package com.kigtts.next.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Animate all roles together, including foregrounds and plugin page colors. */
@Composable
fun animatedColorScheme(target: ColorScheme): ColorScheme {
    @Composable fun color(value: Color, role: String) =
        animateColorAsState(value, tween(450, easing = FastOutSlowInEasing), label = role).value
    return target.copy(
        primary = color(target.primary, "primary"),
        onPrimary = color(target.onPrimary, "onPrimary"),
        primaryContainer = color(target.primaryContainer, "primaryContainer"),
        onPrimaryContainer = color(target.onPrimaryContainer, "onPrimaryContainer"),
        inversePrimary = color(target.inversePrimary, "inversePrimary"),
        secondary = color(target.secondary, "secondary"),
        onSecondary = color(target.onSecondary, "onSecondary"),
        secondaryContainer = color(target.secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = color(target.onSecondaryContainer, "onSecondaryContainer"),
        tertiary = color(target.tertiary, "tertiary"),
        onTertiary = color(target.onTertiary, "onTertiary"),
        tertiaryContainer = color(target.tertiaryContainer, "tertiaryContainer"),
        onTertiaryContainer = color(target.onTertiaryContainer, "onTertiaryContainer"),
        background = color(target.background, "background"),
        onBackground = color(target.onBackground, "onBackground"),
        surface = color(target.surface, "surface"),
        onSurface = color(target.onSurface, "onSurface"),
        surfaceVariant = color(target.surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = color(target.onSurfaceVariant, "onSurfaceVariant"),
        surfaceTint = color(target.surfaceTint, "surfaceTint"),
        inverseSurface = color(target.inverseSurface, "inverseSurface"),
        inverseOnSurface = color(target.inverseOnSurface, "inverseOnSurface"),
        error = color(target.error, "error"),
        onError = color(target.onError, "onError"),
        errorContainer = color(target.errorContainer, "errorContainer"),
        onErrorContainer = color(target.onErrorContainer, "onErrorContainer"),
        outline = color(target.outline, "outline"),
        outlineVariant = color(target.outlineVariant, "outlineVariant"),
        scrim = color(target.scrim, "scrim"),
        surfaceBright = color(target.surfaceBright, "surfaceBright"),
        surfaceDim = color(target.surfaceDim, "surfaceDim"),
        surfaceContainer = color(target.surfaceContainer, "surfaceContainer"),
        surfaceContainerHigh = color(target.surfaceContainerHigh, "surfaceContainerHigh"),
        surfaceContainerHighest = color(target.surfaceContainerHighest, "surfaceContainerHighest"),
        surfaceContainerLow = color(target.surfaceContainerLow, "surfaceContainerLow"),
        surfaceContainerLowest = color(target.surfaceContainerLowest, "surfaceContainerLowest"),
        primaryFixed = color(target.primaryFixed, "primaryFixed"),
        primaryFixedDim = color(target.primaryFixedDim, "primaryFixedDim"),
        onPrimaryFixed = color(target.onPrimaryFixed, "onPrimaryFixed"),
        onPrimaryFixedVariant = color(target.onPrimaryFixedVariant, "onPrimaryFixedVariant"),
        secondaryFixed = color(target.secondaryFixed, "secondaryFixed"),
        secondaryFixedDim = color(target.secondaryFixedDim, "secondaryFixedDim"),
        onSecondaryFixed = color(target.onSecondaryFixed, "onSecondaryFixed"),
        onSecondaryFixedVariant = color(target.onSecondaryFixedVariant, "onSecondaryFixedVariant"),
        tertiaryFixed = color(target.tertiaryFixed, "tertiaryFixed"),
        tertiaryFixedDim = color(target.tertiaryFixedDim, "tertiaryFixedDim"),
        onTertiaryFixed = color(target.onTertiaryFixed, "onTertiaryFixed"),
        onTertiaryFixedVariant = color(target.onTertiaryFixedVariant, "onTertiaryFixedVariant"),
    )
}
