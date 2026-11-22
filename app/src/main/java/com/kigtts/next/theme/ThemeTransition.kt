package com.kigtts.next.theme

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.util.lerp

@Stable
internal class ThemePaint(initial: ThemePalette) {
    var palette by mutableStateOf(initial)
        private set

    suspend fun animateTo(target: ThemePalette) {
        val from = palette
        if (from == target) return
        val blend = PaletteBlend()
        animate(0f, 1f, animationSpec = tween(450, easing = FastOutSlowInEasing)) { fraction, _ ->
            palette = ThemePalette(blend.interpolate(from.colors, target.colors, fraction, drawOnly = true), CompactControlPalette(
                blend.lerp(from.controls.container, target.controls.container, fraction),
                blend.lerp(from.controls.content, target.controls.content, fraction),
                blend.lerp(from.controls.plainContent, target.controls.plainContent, fraction)))
        }
    }
}

/** Content gets the target palette once. Drawing can read the transition without recomposition. */
internal val LocalThemePaint = compositionLocalOf<ThemePaint?> { null }

@Composable internal fun rememberThemePaint(target: ThemePalette): ThemePaint {
    val paint = remember { ThemePaint(target) }
    LaunchedEffect(target) { paint.animateTo(target) }
    return paint
}

/** Animate all roles together, including foregrounds and plugin page colors. */
@Composable
fun animatedColorScheme(target: ColorScheme): ColorScheme {
    var current by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val from = current
        if (from === target) return@LaunchedEffect
        val blend = PaletteBlend()
        animate(0f, 1f, animationSpec = tween(450, easing = FastOutSlowInEasing)) { fraction, _ ->
            current = blend.interpolate(from, target, fraction)
        }
    }
    return current
}

/** One animation clock for every role; interrupted transitions start at the visible palette. */
private class PaletteBlend {
    private val perceptual = HashMap<Color, Color>()

    fun lerp(from: Color, to: Color, fraction: Float): Color {
        if (fraction <= 0f) return from
        if (fraction >= 1f) return to
        if (from == to) return to
        val a = perceptual.getOrPut(from) { from.convert(ColorSpaces.Oklab) }
        val b = perceptual.getOrPut(to) { to.convert(ColorSpaces.Oklab) }
        return Color(lerp(a.red, b.red, fraction), lerp(a.green, b.green, fraction),
            lerp(a.blue, b.blue, fraction), lerp(a.alpha, b.alpha, fraction), ColorSpaces.Oklab).convert(to.colorSpace)
    }

    fun interpolate(from: ColorScheme, target: ColorScheme, fraction: Float, drawOnly: Boolean = false): ColorScheme {
        if (fraction <= 0f) return from
        if (fraction >= 1f) return target
        // The activity already gives Material the target palette once. Only native drawing
        // reads these intermediate frames, so unused fixed and container roles need no conversion.
        fun otherRole(start: Color, end: Color) = if (drawOnly) end else lerp(start, end, fraction)
        return target.copy(
            primary = lerp(from.primary, target.primary, fraction),
            onPrimary = lerp(from.onPrimary, target.onPrimary, fraction),
            primaryContainer = lerp(from.primaryContainer, target.primaryContainer, fraction),
            onPrimaryContainer = lerp(from.onPrimaryContainer, target.onPrimaryContainer, fraction),
            inversePrimary = otherRole(from.inversePrimary, target.inversePrimary),
            secondary = lerp(from.secondary, target.secondary, fraction),
            onSecondary = lerp(from.onSecondary, target.onSecondary, fraction),
            secondaryContainer = otherRole(from.secondaryContainer, target.secondaryContainer),
            onSecondaryContainer = lerp(from.onSecondaryContainer, target.onSecondaryContainer, fraction),
            tertiary = lerp(from.tertiary, target.tertiary, fraction),
            onTertiary = lerp(from.onTertiary, target.onTertiary, fraction),
            tertiaryContainer = otherRole(from.tertiaryContainer, target.tertiaryContainer),
            onTertiaryContainer = lerp(from.onTertiaryContainer, target.onTertiaryContainer, fraction),
            background = lerp(from.background, target.background, fraction),
            onBackground = lerp(from.onBackground, target.onBackground, fraction),
            surface = otherRole(from.surface, target.surface),
            onSurface = lerp(from.onSurface, target.onSurface, fraction),
            surfaceVariant = otherRole(from.surfaceVariant, target.surfaceVariant),
            onSurfaceVariant = lerp(from.onSurfaceVariant, target.onSurfaceVariant, fraction),
            surfaceTint = otherRole(from.surfaceTint, target.surfaceTint),
            inverseSurface = otherRole(from.inverseSurface, target.inverseSurface),
            inverseOnSurface = lerp(from.inverseOnSurface, target.inverseOnSurface, fraction),
            error = lerp(from.error, target.error, fraction),
            onError = lerp(from.onError, target.onError, fraction),
            errorContainer = otherRole(from.errorContainer, target.errorContainer),
            onErrorContainer = lerp(from.onErrorContainer, target.onErrorContainer, fraction),
            outline = lerp(from.outline, target.outline, fraction),
            outlineVariant = lerp(from.outlineVariant, target.outlineVariant, fraction),
            scrim = otherRole(from.scrim, target.scrim),
            surfaceBright = otherRole(from.surfaceBright, target.surfaceBright),
            surfaceDim = otherRole(from.surfaceDim, target.surfaceDim),
            surfaceContainer = lerp(from.surfaceContainer, target.surfaceContainer, fraction),
            surfaceContainerHigh = lerp(from.surfaceContainerHigh, target.surfaceContainerHigh, fraction),
            surfaceContainerHighest = otherRole(from.surfaceContainerHighest, target.surfaceContainerHighest),
            surfaceContainerLow = lerp(from.surfaceContainerLow, target.surfaceContainerLow, fraction),
            surfaceContainerLowest = lerp(from.surfaceContainerLowest, target.surfaceContainerLowest, fraction),
            primaryFixed = otherRole(from.primaryFixed, target.primaryFixed),
            primaryFixedDim = otherRole(from.primaryFixedDim, target.primaryFixedDim),
            onPrimaryFixed = otherRole(from.onPrimaryFixed, target.onPrimaryFixed),
            onPrimaryFixedVariant = otherRole(from.onPrimaryFixedVariant, target.onPrimaryFixedVariant),
            secondaryFixed = otherRole(from.secondaryFixed, target.secondaryFixed),
            secondaryFixedDim = otherRole(from.secondaryFixedDim, target.secondaryFixedDim),
            onSecondaryFixed = otherRole(from.onSecondaryFixed, target.onSecondaryFixed),
            onSecondaryFixedVariant = otherRole(from.onSecondaryFixedVariant, target.onSecondaryFixedVariant),
            tertiaryFixed = otherRole(from.tertiaryFixed, target.tertiaryFixed),
            tertiaryFixedDim = otherRole(from.tertiaryFixedDim, target.tertiaryFixedDim),
            onTertiaryFixed = otherRole(from.onTertiaryFixed, target.onTertiaryFixed),
            onTertiaryFixedVariant = otherRole(from.onTertiaryFixedVariant, target.onTertiaryFixedVariant),
        )
    }
}
