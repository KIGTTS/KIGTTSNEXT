package com.kigtts.next.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.SubtitleAppearance
import com.kigtts.next.theme.*
import com.kigtts.next.ui.home.LocalHomePreviewGeometry

/** Scale actual home layout measurements. Text, icons, media and the listening panel are omitted. */
@Composable internal fun AppearanceHomePreview(settings: ThemeSettings, placeholder: String,
    subtitle: SubtitleAppearance, modifier: Modifier = Modifier) {
    val measured = LocalHomePreviewGeometry.current?.regions?.toMap().orEmpty()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val viewport = measured["viewport"] ?: Rect(0f, 0f, configuration.screenWidthDp * density, configuration.screenHeightDp * density)
    val wide = viewport.width > viewport.height
    val colors = MaterialTheme.colorScheme
    val radius = settings.cornerRadius * density
    val fallback = if (wide) Rect(88f * density, 8f * density, viewport.right - 208f * density, viewport.bottom - 12f * density)
        else Rect(12f * density, 64f * density, viewport.right - 12f * density, viewport.bottom - 212f * density)
    val original = measured["subtitle"] ?: fallback
    // Expand into the omitted listening space while retaining the real subtitle and drawer edges.
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
                    fun panel(rect: Rect, color: androidx.compose.ui.graphics.Color, r: Float = radius) {
                        if (rect.width > 0 && rect.height > 0) drawRoundRect(color, rect.topLeft, rect.size, CornerRadius(r.coerceAtMost(minOf(rect.width, rect.height) / 2)))
                    }
                    panel(main, colors.surfaceContainerHigh.copy(alpha = if (settings.background == null) 1f else .88f))
                    panel(drawer, colors.surfaceContainer.copy(alpha = if (settings.background == null) 1f else .85f))
                    val cards = measured.filterKeys { it.startsWith("phrase/") }.values.filter { it.overlaps(drawer) }
                    cards.forEach { panel(it.intersect(drawer), colors.surfaceContainerLowest, radius * .7f) }
                    if (measured.isEmpty()) {
                        repeat(3) { index ->
                            val left = drawer.left + 8f * density + index * (drawer.width - 16f * density) / 3
                            panel(Rect(left, drawer.top + 48f * density,
                                left + (drawer.width - 24f * density) / 3, drawer.bottom - 48f * density), colors.surfaceContainerLowest)
                        }
                    }
                    // Blank control shapes preserve the actual panel's visual weight without labels or symbols.
                    val control = colors.secondaryContainer
                    val pad = 8f * density; val button = 32f * density
                    panel(Rect(main.left + pad, main.top + pad, main.left + pad + button, main.top + pad + button), control)
                    panel(Rect(main.left + pad, main.bottom - pad - button, main.left + pad + button * 1.8f, main.bottom - pad), control)
                    panel(Rect(main.right - pad - button * 1.8f, main.bottom - pad - button, main.right - pad, main.bottom - pad), control)
                    if (!wide && drawer.height > 64f * density) panel(Rect(drawer.center.x - 38f * density, drawer.top + 22f * density,
                        drawer.center.x + 38f * density, drawer.top + 26f * density), colors.primary, 2f * density)
                }
            }
        }
    }
}
