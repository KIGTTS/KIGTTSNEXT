package com.kigtts.next.desktop

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.background
import androidx.glance.layout.ContentScale
import androidx.glance.layout.size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun <T> desktopWidgetData(initial: T, initialRevision: Int, load: suspend () -> T): T {
    val revision by DesktopUpdates.revision.collectAsState()
    return produceState(initial, revision) {
        if (revision != initialRevision) value = withContext(Dispatchers.IO) { load() }
    }.value
}

@Composable
internal fun GlanceModifier.desktopSurface(width: Float, height: Float, color: Color,
    corners: List<Float>): GlanceModifier {
    val background = remember(width, height, color, corners) {
        desktopBackground(width, height, color.toArgb(), corners)
    }
    return background(ImageProvider(background), contentScale = ContentScale.FillBounds)
}

@Composable
internal fun DesktopGlyph(entry: DesktopEntry, ink: Color, edge: Int) {
    val context: Context = LocalContext.current
    val glyph = remember(context, entry, ink) { desktopGlyph(context, entry.icon, ink.toArgb()) }
    if (glyph != null) Image(ImageProvider(glyph), null, GlanceModifier.size(edge.dp))
}
