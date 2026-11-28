package com.kigtts.next.desktop

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.kigtts.next.i18n.uiCopy

internal object QuickEntryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val revision = DesktopUpdates.revision.value
        val initial = QuickEntryData.load(context)
        provideContent { QuickEntries(desktopWidgetData(initial, revision) { QuickEntryData.load(context) }) }
    }
}

private data class QuickEntryData(val appearance: DesktopAppearance, val labels: Map<DesktopEntry, String>) {
    companion object {
        fun load(context: Context) = QuickEntryData(DesktopAppearance.load(context),
            (listOf(DesktopEntry.HOME) + DesktopEntry.quick).associateWith { entry ->
                if (entry == DesktopEntry.HOME) "KIGTTS" else context.uiCopy(entry.label)
            })
    }
}

@Composable
private fun QuickEntries(data: QuickEntryData) {
    val size = LocalSize.current
    val width = size.width.value
    val height = size.height.value
    val corner = (data.appearance.corner + 8f).coerceAtMost(28f)
    val colors = data.appearance.colors
    val stacked = width < 250f && height >= 108f
    val inset = if (height < 56f) 3f else 6f
    val logoWidth = (width * if (stacked) .32f else .22f).coerceIn(38f, 82f)
    val buttonWidth = (width - logoWidth - inset * 2 - if (stacked) 4f else 12f).coerceAtLeast(4f)
    val innerHeight = (height - inset * 2).coerceAtLeast(4f)
    val palette = listOf(colors.primaryContainer to colors.onPrimaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer, colors.tertiaryContainer to colors.onTertiaryContainer)
    Row(GlanceModifier.fillMaxSize().desktopSurface(width, height, colors.surfaceContainer, List(4) { corner })
        .padding(inset.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        QuickEntry(DesktopEntry.HOME, data, GlanceModifier.width(logoWidth.dp).fillMaxHeight(),
            logoWidth, innerHeight, colors.primary, colors.onPrimary, List(4) { corner },
            minOf(logoWidth, innerHeight).toInt().coerceIn(22, 50))
        Spacer(GlanceModifier.width(4.dp))
        if (stacked) {
            val buttonHeight = (innerHeight - 6f) / 3f
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                DesktopEntry.quick.forEachIndexed { index, entry ->
                    if (index > 0) Spacer(GlanceModifier.height(3.dp))
                    val (fill, ink) = palette[index]
                    QuickEntry(entry, data, GlanceModifier.fillMaxWidth().defaultWeight(),
                        buttonWidth, buttonHeight, fill, ink,
                        entryCorners(index, true, corner), (buttonHeight * .64f).toInt().coerceIn(17, 28))
                }
            }
        } else {
            DesktopEntry.quick.forEachIndexed { index, entry ->
                if (index > 0) Spacer(GlanceModifier.width(4.dp))
                val (fill, ink) = palette[index]
                val edge = minOf(buttonWidth / 3f, innerHeight)
                QuickEntry(entry, data, GlanceModifier.defaultWeight().fillMaxHeight(), buttonWidth / 3f,
                    innerHeight, fill, ink, entryCorners(index, false, corner), (edge * .52f).toInt().coerceIn(17, 32))
            }
        }
    }
}

private fun entryCorners(index: Int, stacked: Boolean, outer: Float): List<Float> {
    val inner = (outer * .32f).coerceIn(4f, 9f)
    return when (index) {
        0 -> if (stacked) listOf(outer, outer, inner, inner) else listOf(outer, inner, inner, outer)
        2 -> if (stacked) listOf(inner, inner, outer, outer) else listOf(inner, outer, outer, inner)
        else -> List(4) { inner }
    }
}

@Composable
private fun QuickEntry(entry: DesktopEntry, data: QuickEntryData, modifier: GlanceModifier,
    width: Float, height: Float, fill: Color, ink: Color, corners: List<Float>, glyph: Int) {
    val context = LocalContext.current
    Box(modifier.desktopSurface(width, height, fill, corners)
        .semantics { contentDescription = data.labels.getValue(entry) }
        .clickable(actionStartActivity(entry.intent(context))), contentAlignment = Alignment.Center) {
        DesktopGlyph(entry, ink, glyph)
    }
}
