package com.kigtts.next.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kigtts.next.R
import com.kigtts.next.state.SubtitleAppearance
import com.kigtts.next.theme.*
import com.kigtts.next.ui.home.LocalHomePreviewGeometry

/** Scale measured home geometry, retaining blank controls and branding but no user content. */
@Composable internal fun AppearanceHomePreview(settings: ThemeSettings, placeholder: String,
    subtitle: SubtitleAppearance, modifier: Modifier = Modifier) {
    val measured = LocalHomePreviewGeometry.current?.regions?.toMap().orEmpty()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val viewport = measured["viewport"] ?: Rect(0f, 0f, configuration.screenWidthDp * density, configuration.screenHeightDp * density)
    val wide = viewport.width > viewport.height
    val colors = MaterialTheme.colorScheme
    // The host deliberately raises compact light controls to medium contrast. Share its animated roles.
    val controls = LocalCompactControlPalette.current ?: compactControlPalette(settings,
        dark = settings.mode.usesDark(isSystemInDarkTheme()), base = colors)
    val logo = painterResource(R.drawable.logo_horizontal)
    val radius = settings.cornerRadius * density
    val fallback = if (wide) Rect(88f * density, 8f * density, viewport.right - 208f * density, viewport.bottom - 12f * density)
        else Rect(12f * density, 64f * density, viewport.right - 12f * density, viewport.bottom - 212f * density)
    val original = measured["subtitle"] ?: fallback
    // Omit the listening panel; keep the actual subtitle bottom and drawer edges.
    val main = if (wide) original.copy(left = viewport.left + 88f * density)
        else original.copy(top = viewport.top + 64f * density)
    val drawer = measured["phrases"] ?: if (wide)
        Rect(viewport.right - 196f * density, 8f * density, viewport.right - 12f * density, viewport.bottom - 12f * density)
        else Rect(12f * density, viewport.bottom - 200f * density, viewport.right - 12f * density, viewport.bottom - 4f * density)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth.value * density / viewport.width, maxHeight.value * density / viewport.height)
        Box(Modifier.size((viewport.width * scale / density).dp, (viewport.height * scale / density).dp)
            .clip(MaterialTheme.shapes.large).testTag("appearance-home-thumbnail")) {
            AppearanceBackdrop(settings, Modifier.matchParentSize())
            Canvas(Modifier.matchParentSize().testTag("appearance-home-wireframe")) {
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
                    listOf(Offset(main.left + pad, main.top + pad), Offset(main.right - pad - button, main.top + pad),
                        Offset(main.left + pad, main.bottom - pad - button), Offset(main.right - pad - button, main.bottom - pad - button))
                        .forEach { circle(Rect(it, Size(button, button))) }
                    val headerStart = measured["header-start"] ?: if (wide)
                        Rect(8f * density, viewport.bottom - 56f * density, 56f * density, viewport.bottom - 8f * density)
                        else Rect(8f * density, viewport.top + 8f * density, 56f * density, viewport.top + 56f * density)
                    val headerEnd = measured["header-end"] ?: if (wide)
                        Rect(8f * density, 8f * density, 56f * density, 56f * density)
                        else Rect(viewport.right - 56f * density, viewport.top + 8f * density, viewport.right - 8f * density, viewport.top + 56f * density)
                    circle(headerStart, controls.plainContent); circle(headerEnd, controls.plainContent)
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
                            panel(bounds, if (selected) colors.primaryContainer else Color.Transparent, corner)
                            if (!selected) drawRoundRect(colors.outlineVariant, bounds.topLeft, bounds.size,
                                CornerRadius(corner.coerceAtMost(bounds.height / 2)), style = Stroke(density))
                            val content = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
                            drawCircle(content, 6f * density, Offset(bounds.left + 16f * density, bounds.center.y))
                        }
                    }
                }
            }
        }
    }
}
