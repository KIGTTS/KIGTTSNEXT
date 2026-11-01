package com.kigtts.next.theme

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.lerp

/** Animate all roles together, including foregrounds and plugin page colors. */
@Composable
fun animatedColorScheme(target: ColorScheme): ColorScheme {
    var current by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val from = current
        if (from === target) return@LaunchedEffect
        animate(0f, 1f, animationSpec = tween(450, easing = FastOutSlowInEasing)) { fraction, _ ->
            current = interpolateTheme(from, target, fraction)
        }
    }
    return current
}

/** One animation clock for every role; interrupted transitions start at the visible palette. */
internal fun interpolateTheme(from: ColorScheme, target: ColorScheme, fraction: Float): ColorScheme {
    if (fraction <= 0f) return from
    if (fraction >= 1f) return target
    return target.copy(
        primary = lerp(from.primary, target.primary, fraction),
        onPrimary = lerp(from.onPrimary, target.onPrimary, fraction),
        primaryContainer = lerp(from.primaryContainer, target.primaryContainer, fraction),
        onPrimaryContainer = lerp(from.onPrimaryContainer, target.onPrimaryContainer, fraction),
        inversePrimary = lerp(from.inversePrimary, target.inversePrimary, fraction),
        secondary = lerp(from.secondary, target.secondary, fraction),
        onSecondary = lerp(from.onSecondary, target.onSecondary, fraction),
        secondaryContainer = lerp(from.secondaryContainer, target.secondaryContainer, fraction),
        onSecondaryContainer = lerp(from.onSecondaryContainer, target.onSecondaryContainer, fraction),
        tertiary = lerp(from.tertiary, target.tertiary, fraction),
        onTertiary = lerp(from.onTertiary, target.onTertiary, fraction),
        tertiaryContainer = lerp(from.tertiaryContainer, target.tertiaryContainer, fraction),
        onTertiaryContainer = lerp(from.onTertiaryContainer, target.onTertiaryContainer, fraction),
        background = lerp(from.background, target.background, fraction),
        onBackground = lerp(from.onBackground, target.onBackground, fraction),
        surface = lerp(from.surface, target.surface, fraction),
        onSurface = lerp(from.onSurface, target.onSurface, fraction),
        surfaceVariant = lerp(from.surfaceVariant, target.surfaceVariant, fraction),
        onSurfaceVariant = lerp(from.onSurfaceVariant, target.onSurfaceVariant, fraction),
        surfaceTint = lerp(from.surfaceTint, target.surfaceTint, fraction),
        inverseSurface = lerp(from.inverseSurface, target.inverseSurface, fraction),
        inverseOnSurface = lerp(from.inverseOnSurface, target.inverseOnSurface, fraction),
        error = lerp(from.error, target.error, fraction),
        onError = lerp(from.onError, target.onError, fraction),
        errorContainer = lerp(from.errorContainer, target.errorContainer, fraction),
        onErrorContainer = lerp(from.onErrorContainer, target.onErrorContainer, fraction),
        outline = lerp(from.outline, target.outline, fraction),
        outlineVariant = lerp(from.outlineVariant, target.outlineVariant, fraction),
        scrim = lerp(from.scrim, target.scrim, fraction),
        surfaceBright = lerp(from.surfaceBright, target.surfaceBright, fraction),
        surfaceDim = lerp(from.surfaceDim, target.surfaceDim, fraction),
        surfaceContainer = lerp(from.surfaceContainer, target.surfaceContainer, fraction),
        surfaceContainerHigh = lerp(from.surfaceContainerHigh, target.surfaceContainerHigh, fraction),
        surfaceContainerHighest = lerp(from.surfaceContainerHighest, target.surfaceContainerHighest, fraction),
        surfaceContainerLow = lerp(from.surfaceContainerLow, target.surfaceContainerLow, fraction),
        surfaceContainerLowest = lerp(from.surfaceContainerLowest, target.surfaceContainerLowest, fraction),
        primaryFixed = lerp(from.primaryFixed, target.primaryFixed, fraction),
        primaryFixedDim = lerp(from.primaryFixedDim, target.primaryFixedDim, fraction),
        onPrimaryFixed = lerp(from.onPrimaryFixed, target.onPrimaryFixed, fraction),
        onPrimaryFixedVariant = lerp(from.onPrimaryFixedVariant, target.onPrimaryFixedVariant, fraction),
        secondaryFixed = lerp(from.secondaryFixed, target.secondaryFixed, fraction),
        secondaryFixedDim = lerp(from.secondaryFixedDim, target.secondaryFixedDim, fraction),
        onSecondaryFixed = lerp(from.onSecondaryFixed, target.onSecondaryFixed, fraction),
        onSecondaryFixedVariant = lerp(from.onSecondaryFixedVariant, target.onSecondaryFixedVariant, fraction),
        tertiaryFixed = lerp(from.tertiaryFixed, target.tertiaryFixed, fraction),
        tertiaryFixedDim = lerp(from.tertiaryFixedDim, target.tertiaryFixedDim, fraction),
        onTertiaryFixed = lerp(from.onTertiaryFixed, target.onTertiaryFixed, fraction),
        onTertiaryFixedVariant = lerp(from.onTertiaryFixedVariant, target.onTertiaryFixedVariant, fraction),
    )
}
