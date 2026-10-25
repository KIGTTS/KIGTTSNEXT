package com.kigtts.next.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hct.Hct
import dynamiccolor.MaterialDynamicColors
import dynamiccolor.DynamicScheme
import dynamiccolor.ColorSpec.SpecVersion
import scheme.SchemeTonalSpot
import com.kigtts.next.ui.components.LocalCornerRadius

fun themeColors(settings: ThemeSettings, wallpaperColors: ColorScheme? = null,
    isDark: Boolean = settings.mode.usesDark(true)): ColorScheme {
    val seed = wallpaperColors?.primary?.toArgb() ?: settings.seed.toInt()
    val scheme = SchemeTonalSpot(Hct.fromInt(seed), isDark, settings.contrast.value.toDouble(), SpecVersion.SPEC_2025, DynamicScheme.Platform.PHONE)
    val c = MaterialDynamicColors()
    val deeper = settings.deeperDark && isDark
    return (if (isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = Color(c.primary().getArgb(scheme)), onPrimary = Color(c.onPrimary().getArgb(scheme)),
        primaryContainer = Color(c.primaryContainer().getArgb(scheme)), onPrimaryContainer = Color(c.onPrimaryContainer().getArgb(scheme)),
        secondary = Color(c.secondary().getArgb(scheme)), onSecondary = Color(c.onSecondary().getArgb(scheme)),
        secondaryContainer = Color(c.secondaryContainer().getArgb(scheme)), onSecondaryContainer = Color(c.onSecondaryContainer().getArgb(scheme)),
        tertiary = Color(c.tertiary().getArgb(scheme)), onTertiary = Color(c.onTertiary().getArgb(scheme)),
        tertiaryContainer = Color(c.tertiaryContainer().getArgb(scheme)), onTertiaryContainer = Color(c.onTertiaryContainer().getArgb(scheme)),
        background = if (deeper) Color(scheme.neutralPalette.tone(0)) else Color(c.background().getArgb(scheme)),
        onBackground = Color(c.onBackground().getArgb(scheme)),
        surface = if (deeper) Color(scheme.neutralPalette.tone(0)) else Color(c.surface().getArgb(scheme)),
        onSurface = Color(c.onSurface().getArgb(scheme)),
        surfaceVariant = Color(c.surfaceVariant().getArgb(scheme)), onSurfaceVariant = Color(c.onSurfaceVariant().getArgb(scheme)),
        surfaceContainerLowest = if (deeper) Color(scheme.neutralPalette.tone(0)) else Color(c.surfaceContainerLowest().getArgb(scheme)),
        surfaceContainerLow = if (deeper) Color(scheme.neutralPalette.tone(4)) else Color(c.surfaceContainerLow().getArgb(scheme)),
        surfaceContainer = if (deeper) Color(scheme.neutralPalette.tone(6)) else Color(c.surfaceContainer().getArgb(scheme)),
        surfaceContainerHigh = if (deeper) Color(scheme.neutralPalette.tone(10)) else Color(c.surfaceContainerHigh().getArgb(scheme)),
        surfaceContainerHighest = if (deeper) Color(scheme.neutralPalette.tone(12)) else Color(c.surfaceContainerHighest().getArgb(scheme)),
        outline = Color(c.outline().getArgb(scheme)), outlineVariant = Color(c.outlineVariant().getArgb(scheme)),
        error = Color(c.error().getArgb(scheme)), onError = Color(c.onError().getArgb(scheme)),
        errorContainer = Color(c.errorContainer().getArgb(scheme)), onErrorContainer = Color(c.onErrorContainer().getArgb(scheme)),
        inverseSurface = Color(c.inverseSurface().getArgb(scheme)), inverseOnSurface = Color(c.inverseOnSurface().getArgb(scheme)),
        inversePrimary = Color(c.inversePrimary().getArgb(scheme)), surfaceTint = Color(c.primary().getArgb(scheme)),
        surfaceBright = Color(c.surfaceBright().getArgb(scheme)), surfaceDim = Color(c.surfaceDim().getArgb(scheme)),
        primaryFixed = Color(c.primaryFixed().getArgb(scheme)), primaryFixedDim = Color(c.primaryFixedDim().getArgb(scheme)),
        onPrimaryFixed = Color(c.onPrimaryFixed().getArgb(scheme)), onPrimaryFixedVariant = Color(c.onPrimaryFixedVariant().getArgb(scheme)),
        secondaryFixed = Color(c.secondaryFixed().getArgb(scheme)), secondaryFixedDim = Color(c.secondaryFixedDim().getArgb(scheme)),
        onSecondaryFixed = Color(c.onSecondaryFixed().getArgb(scheme)), onSecondaryFixedVariant = Color(c.onSecondaryFixedVariant().getArgb(scheme)),
        tertiaryFixed = Color(c.tertiaryFixed().getArgb(scheme)), tertiaryFixedDim = Color(c.tertiaryFixedDim().getArgb(scheme)),
        onTertiaryFixed = Color(c.onTertiaryFixed().getArgb(scheme)), onTertiaryFixedVariant = Color(c.onTertiaryFixedVariant().getArgb(scheme)),
    ).let { generated ->
        // At default contrast preserve the exact platform-provided color roles.
        if (wallpaperColors != null && settings.contrast == ContrastLevel.DEFAULT) {
            if (deeper) wallpaperColors.copy(
                background = generated.background, surface = generated.surface,
                surfaceContainerLowest = generated.surfaceContainerLowest, surfaceContainerLow = generated.surfaceContainerLow,
                surfaceContainer = generated.surfaceContainer, surfaceContainerHigh = generated.surfaceContainerHigh,
                surfaceContainerHighest = generated.surfaceContainerHighest,
            ) else wallpaperColors
        } else generated
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KigttsTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val dark = settings.mode.usesDark(isSystemInDarkTheme())
    val appearanceFont = rememberAppearanceFont(settings.font)
    SystemBarAppearance(dark)
    val (colors, controls) = remember(settings.seed, settings.contrast, settings.deeperDark, settings.wallpaperEnabled, configuration, dark) {
        val wallpaper = if (settings.wallpaperEnabled && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else null
        val colors = themeColors(settings, wallpaper, dark)
        colors to compactControlPalette(settings, wallpaper, dark, colors)
    }
    val radius by animateFloatAsState(settings.cornerRadius,tween(220,easing=FastOutSlowInEasing),label="theme-corner-radius")
    val shapes = Shapes(
        extraSmall = RoundedCornerShape((radius * .3f).coerceAtLeast(4f).dp),
        small = RoundedCornerShape((radius * .6f).coerceAtLeast(4f).dp),
        medium = RoundedCornerShape(radius.dp), large = RoundedCornerShape((radius + 4).coerceAtMost(24f).dp),
        extraLarge = RoundedCornerShape((radius + 8).coerceAtMost(24f).dp),
    )
    MaterialExpressiveTheme(
        colorScheme = animatedColorScheme(colors), shapes = shapes, motionScheme = MotionScheme.expressive(),
        typography = Typography(
            displayMedium = TextStyle(fontSize = 38.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        ).withFont(appearanceFont), content = { CompositionLocalProvider(LocalCornerRadius provides radius.dp,
            LocalAppearanceFont provides appearanceFont,
            LocalCompactControlPalette provides animatedCompactControlPalette(controls), content = content) },
    )
}
