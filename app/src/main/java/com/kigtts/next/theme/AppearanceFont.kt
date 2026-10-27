package com.kigtts.next.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppearanceFont(val family: FontFamily? = null, val heightScale: Float = 1f) {
    fun apply(style: TextStyle): TextStyle = if (family == null) style else style.copy(fontFamily = family,
        fontSize = style.fontSize * heightScale, platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both))
}
val LocalAppearanceFont = staticCompositionLocalOf { AppearanceFont() }

@Composable internal fun rememberAppearanceFont(key: String?, weight: Int = 400): AppearanceFont {
    val context = LocalContext.current
    val resolver = LocalFontFamilyResolver.current
    val font by produceState(AppearanceFont(), key, weight, resolver) {
        value = withContext(Dispatchers.IO) {
            runCatching { AppearanceAssets(context).file(key)?.let {
                loadAppearanceFont(it, weight).also { loaded -> loaded.family?.let { family -> resolver.preload(family) } }
            } ?: AppearanceFont() }.getOrDefault(AppearanceFont())
        }
    }
    return font
}

internal fun Typography.withFont(font: AppearanceFont) = copy(
    displayLarge = font.apply(displayLarge), displayMedium = font.apply(displayMedium), displaySmall = font.apply(displaySmall),
    headlineLarge = font.apply(headlineLarge), headlineMedium = font.apply(headlineMedium), headlineSmall = font.apply(headlineSmall),
    titleLarge = font.apply(titleLarge), titleMedium = font.apply(titleMedium), titleSmall = font.apply(titleSmall),
    bodyLarge = font.apply(bodyLarge), bodyMedium = font.apply(bodyMedium), bodySmall = font.apply(bodySmall),
    labelLarge = font.apply(labelLarge), labelMedium = font.apply(labelMedium), labelSmall = font.apply(labelSmall))
