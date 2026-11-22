package com.kigtts.next.theme

import android.util.LruCache
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

private val standardForegrounds: List<(ThemePalette) -> Color> = listOf(
    { it.colors.onSurface }, { it.colors.onSurfaceVariant }, { it.colors.onBackground },
    { it.colors.onPrimary }, { it.colors.onPrimaryContainer }, { it.colors.onSecondary },
    { it.colors.onSecondaryContainer }, { it.colors.onTertiary }, { it.colors.onTertiaryContainer },
    { it.colors.primary }, { it.colors.secondary }, { it.colors.tertiary },
    { it.colors.inverseOnSurface }, { it.colors.onError }, { it.colors.onErrorContainer },
    { it.colors.error }, { it.colors.outline }, { it.colors.outlineVariant },
)
private val compactForegrounds: List<(ThemePalette) -> Color> = listOf({ it.controls.content }, { it.controls.plainContent })

/** Material and compact foregrounds use the same clock as their containers. Tints outside the palette stay fixed. */
@Composable internal fun rememberThemeTint(color: Color): ColorProducer? {
    val paint = LocalThemePaint.current ?: return null
    val scheme = MaterialTheme.colorScheme
    val compact = LocalCompactControlPalette.current ?: return null
    val useCompact = LocalUseCompactControlPalette.current
    return remember(paint, scheme, compact, color, useCompact) {
        val target = ThemePalette(scheme, compact)
        val preferred = if (useCompact) compactForegrounds else standardForegrounds
        val secondary = if (useCompact) standardForegrounds else compactForegrounds
        val role = preferred.firstOrNull { it(target) == color } ?: secondary.firstOrNull { it(target) == color }
        role?.let { selector ->
            val current = derivedStateOf { selector(paint.palette) }
            ColorProducer { current.value }
        }
    }
}

@Composable internal fun ThemeText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null) {
    val tint = if (color != Color.Unspecified) color else if (style.color != Color.Unspecified) style.color else LocalContentColor.current
    val live = rememberThemeTint(tint)
    if (live == null) Text(text, modifier, color = color, style = style, maxLines = maxLines, overflow = overflow, textAlign = textAlign)
    else BasicText(text, modifier, style = if (textAlign == null) style else style.copy(textAlign = textAlign),
        maxLines = maxLines, overflow = overflow, color = live)
}

@Composable internal fun ThemeIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current) = ThemeIcon(rememberVectorPainter(imageVector), contentDescription, modifier, tint)

@Composable internal fun ThemeIcon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current) {
    val live = rememberThemeTint(tint)
    if (live == null) Icon(painter, contentDescription, modifier, tint)
    else Icon(remember(painter, live) { ThemeTintPainter(painter, live) }, contentDescription, modifier, Color.Unspecified)
}

private val iconFilters = LruCache<Color, ColorFilter>(32)

private class ThemeTintPainter(private val painter: Painter, private val tint: ColorProducer) : Painter() {
    override val intrinsicSize: Size get() = painter.intrinsicSize
    override fun DrawScope.onDraw() {
        val color = tint()
        val filter = iconFilters[color] ?: ColorFilter.tint(color).also { iconFilters.put(color, it) }
        with(painter) { draw(size, colorFilter = filter) }
    }
}
