package com.kigtts.next.media

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb

/** Preserve the host's semantic roles, including custom/dynamic and fixed accents. */
internal fun webThemeColors(c: ColorScheme): Map<String, String> = mapOf(
    "primary" to c.primary, "on-primary" to c.onPrimary,
    "primary-container" to c.primaryContainer, "on-primary-container" to c.onPrimaryContainer,
    "inverse-primary" to c.inversePrimary,
    "secondary" to c.secondary, "on-secondary" to c.onSecondary,
    "secondary-container" to c.secondaryContainer, "on-secondary-container" to c.onSecondaryContainer,
    "tertiary" to c.tertiary, "on-tertiary" to c.onTertiary,
    "tertiary-container" to c.tertiaryContainer, "on-tertiary-container" to c.onTertiaryContainer,
    "background" to c.background, "on-background" to c.onBackground,
    "surface" to c.surface, "on-surface" to c.onSurface,
    "surface-variant" to c.surfaceVariant, "on-surface-variant" to c.onSurfaceVariant,
    "surface-tint" to c.surfaceTint, "inverse-surface" to c.inverseSurface,
    "inverse-on-surface" to c.inverseOnSurface,
    "surface-bright" to c.surfaceBright, "surface-dim" to c.surfaceDim,
    "surface-container-lowest" to c.surfaceContainerLowest,
    "surface-container-low" to c.surfaceContainerLow, "surface-container" to c.surfaceContainer,
    "surface-container-high" to c.surfaceContainerHigh,
    "surface-container-highest" to c.surfaceContainerHighest,
    "outline" to c.outline, "outline-variant" to c.outlineVariant, "scrim" to c.scrim,
    "error" to c.error, "on-error" to c.onError,
    "error-container" to c.errorContainer, "on-error-container" to c.onErrorContainer,
    "primary-fixed" to c.primaryFixed, "primary-fixed-dim" to c.primaryFixedDim,
    "on-primary-fixed" to c.onPrimaryFixed, "on-primary-fixed-variant" to c.onPrimaryFixedVariant,
    "secondary-fixed" to c.secondaryFixed, "secondary-fixed-dim" to c.secondaryFixedDim,
    "on-secondary-fixed" to c.onSecondaryFixed, "on-secondary-fixed-variant" to c.onSecondaryFixedVariant,
    "tertiary-fixed" to c.tertiaryFixed, "tertiary-fixed-dim" to c.tertiaryFixedDim,
    "on-tertiary-fixed" to c.onTertiaryFixed, "on-tertiary-fixed-variant" to c.onTertiaryFixedVariant,
).mapValues { "#%06x".format(it.value.toArgb() and 0xffffff) }
