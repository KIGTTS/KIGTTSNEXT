package com.kigtts.next.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kigtts.next.R
import com.kigtts.next.state.SubtitleAppearance
import com.kigtts.next.theme.*

/** Fixed portrait home thumbnail, independent of live drawer, orientation and listening-panel geometry. */
@Composable internal fun AppearanceHomePreview(settings: ThemeSettings, placeholder: String,
    subtitle: SubtitleAppearance, modifier: Modifier = Modifier) {
    val measured = emptyMap<String, Rect>()
    val density = LocalDensity.current.density
    val viewport = Rect(0f, 0f, 400f * density, 832f * density)
    val wide = viewport.width > viewport.height
    val colors = MaterialTheme.colorScheme
    // The host deliberately raises compact light controls to medium contrast. Share its animated roles.
    val controls = LocalCompactControlPalette.current ?: compactControlPalette(settings,
        dark = settings.mode.usesDark(isSystemInDarkTheme()), base = colors)
    val logo = painterResource(R.drawable.logo_kigtts_wordmark)
    val readIcon = rememberVectorPainter(Icons.AutoMirrored.Filled.VolumeOff)
    val settingsIcon = rememberVectorPainter(Icons.Default.Settings)
    val radius = settings.cornerRadius * density
    val fallback = if (wide) Rect(88f * density, 8f * density, viewport.right - 208f * density, viewport.bottom - 12f * density)
        else Rect(12f * density, 64f * density, viewport.right - 12f * density, 452f * density)
    val original = measured["subtitle"] ?: fallback
    // Omit the listening panel; keep the actual subtitle bottom and drawer edges.
    val main = if (wide) original.copy(left = viewport.left + 88f * density)
        else original.copy(top = viewport.top + 64f * density)
    val sample = rememberTextMeasurer().measure("你好", LocalAppearanceFont.current.apply(TextStyle(
        color = colors.onSurface, fontSize = (minOf(main.width * .26f, main.height * .32f) / density).sp,
        fontWeight = if (subtitle.bold) FontWeight.Bold else FontWeight.Normal,
        platformStyle = PlatformTextStyle(includeFontPadding = false))))
    val drawer = measured["phrases"] ?: if (wide)
        Rect(viewport.right - 196f * density, 8f * density, viewport.right - 12f * density, viewport.bottom - 12f * density)
        else Rect(12f * density, 456f * density, viewport.right - 12f * density, 818f * density)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth.value * density / viewport.width, maxHeight.value * density / viewport.height)
        Box(Modifier.size((viewport.width * scale / density).dp, (viewport.height * scale / density).dp)
            .clip(RoundedCornerShape(4.dp)).testTag("appearance-home-thumbnail")) {
            AppearanceBackdrop(settings, Modifier.matchParentSize(), screenAnchored = false)
            Canvas(Modifier.matchParentSize().testTag("appearance-home-wireframe")
                .semantics { contentDescription = "主界面预览，示例文字：你好" }) {
                withTransform({ scale(scale, scale, Offset.Zero); translate(-viewport.left, -viewport.top) }) {
                    fun panel(rect: Rect, color: Color, r: Float = radius) {
                        if (rect.width > 0 && rect.height > 0) drawRoundRect(color, rect.topLeft, rect.size,
                            CornerRadius(r.coerceAtMost(minOf(rect.width, rect.height) / 2)))
                    }
                    val pad = 8f * density; val button = 32f * density
                    fun circle(rect: Rect, color: Color = controls.container) {
                        if (rect.width > 0 && rect.height > 0) drawCircle(color,
                            minOf(rect.width, rect.height, button) / 2, rect.center)
                    }
                    panel(main, colors.surfaceContainerHigh.copy(alpha = if (settings.background == null) 1f else .88f))
                    drawText(sample, topLeft = Offset(main.center.x - sample.size.width / 2, main.center.y - sample.size.height / 2))
                    panel(drawer, colors.surfaceContainer.copy(alpha = if (settings.background == null) 1f else .85f))
                    val cards = measured.filterKeys { it.startsWith("phrase/") }.values.filter { it.overlaps(drawer) }
                    cards.forEach { panel(it.intersect(drawer), colors.surfaceContainerLowest, radius * .7f) }
                    if (measured.isEmpty()) {
                        repeat(6) { index ->
                            val cell = (drawer.width - 3 * pad) / 2
                            val height = (drawer.height - 96f * density - 2 * pad) / 3
                            val left = drawer.left + pad + index % 2 * (cell + pad)
                            val top = drawer.top + 48f * density + index / 2 * (height + pad)
                            panel(Rect(left, top, left + cell, top + height), colors.surfaceContainerLowest)
                        }
                    }
                    val pillWidth = button * 1.8f
                    listOf(Rect(main.left + pad, main.top + pad, main.left + pad + pillWidth, main.top + pad + button),
                        Rect(main.left + pad, main.bottom - pad - button, main.left + pad + pillWidth, main.bottom - pad),
                        Rect(main.right - pad - pillWidth, main.bottom - pad - button, main.right - pad, main.bottom - pad))
                        .forEach { panel(it, controls.container, button / 2) }
                    circle(Rect(main.right - pad - button, main.top + pad, main.right - pad, main.top + pad + button))
                    val headerStart = measured["header-start"] ?: if (wide)
                        Rect(8f * density, viewport.bottom - 56f * density, 56f * density, viewport.bottom - 8f * density)
                        else Rect(8f * density, viewport.top + 8f * density, 56f * density, viewport.top + 56f * density)
                    val headerEnd = measured["header-end"] ?: if (wide)
                        Rect(8f * density, 8f * density, 56f * density, 56f * density)
                        else Rect(viewport.right - 56f * density, viewport.top + 8f * density, viewport.right - 8f * density, viewport.top + 56f * density)
                    listOf(headerStart to readIcon, headerEnd to settingsIcon).forEach { (rect, painter) ->
                        val size = Size(24f * density, 24f * density)
                        withTransform({ translate(rect.center.x - size.width / 2, rect.center.y - size.height / 2) }) {
                            with(painter) { draw(size, colorFilter = ColorFilter.tint(controls.plainContent)) }
                        }
                    }
                    val logoRect = measured["header-logo"] ?: Rect(
                        Offset(if (wide) 32f * density - 64f * density else viewport.center.x - 64f * density,
                            if (wide) viewport.center.y - 16f * density else viewport.top + 16f * density), Size(128f * density, 32f * density))
                    withTransform({ if (wide) rotate(-90f, logoRect.center); translate(logoRect.left, logoRect.top) }) {
                        with(logo) { draw(logoRect.size, colorFilter = ColorFilter.tint(colors.onSurface)) }
                    }
                    circle(measured["dock-start"] ?: Rect(drawer.left + pad, drawer.top + pad, drawer.left + pad + button, drawer.top + pad + button))
                    circle(measured["dock-end"] ?: Rect(drawer.right - pad - button, drawer.top + pad, drawer.right - pad, drawer.top + pad + button))
                    val handle = measured["dock-handle"] ?: if (wide)
                        Rect(drawer.left + 22f * density, drawer.center.y - 38f * density, drawer.left + 26f * density, drawer.center.y + 38f * density)
                        else Rect(drawer.center.x - 38f * density, drawer.top + 22f * density, drawer.center.x + 38f * density, drawer.top + 26f * density)
                    panel(handle, colors.primary, 2f * density)
                    val tabs = measured.filterKeys { it.startsWith("tab/") || it.startsWith("tab-selected/") }
                    val tabBar = measured["category-bar"] ?: Rect(drawer.left + pad, drawer.bottom - 48f * density, drawer.right - pad, drawer.bottom)
                    val shown = tabs.ifEmpty { (0..2).associate { index ->
                        (if (index == 0) "tab-selected/" else "tab/") + index to Rect(
                            tabBar.left + index * tabBar.width / 3, tabBar.center.y - 16f * density,
                            tabBar.left + (index + 1) * tabBar.width / 3 - 4f * density, tabBar.center.y + 16f * density)
                    } }
                    shown.forEach { (key, rect) ->
                        if (rect.overlaps(drawer) && rect.overlaps(tabBar)) {
                            val selected = key.startsWith("tab-selected/")
                            val bounds = rect.intersect(drawer).intersect(tabBar)
                            val corner = (if (selected) 16f else 8f) * density
                            if (selected) panel(bounds, colors.primaryContainer, corner)
                            else drawRoundRect(colors.outlineVariant, bounds.topLeft, bounds.size,
                                CornerRadius(corner.coerceAtMost(bounds.height / 2)), style = Stroke(density))
                        }
                    }
                }
            }
        }
    }
}
