package com.kigtts.next.ui.components

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp

/** Offline Material Symbols Rounded. Glyph outlines become normal tintable Compose vectors. */
object MaterialSymbols {
    @Volatile private var context: Context? = null
    fun initialize(value: Context) { context = value.applicationContext; com.kigtts.next.icons.MaterialSymbolCatalog.initialize(value) }
    private val font by lazy { Typeface.createFromAsset(requireNotNull(context).assets, "icons/material-symbols-rounded.ttf") }
    private val cache = android.util.LruCache<String, ImageVector>(96)
    fun search(query: String, offset: Int = 0, limit: Int = 80): List<String> = com.kigtts.next.icons.MaterialSymbolCatalog.search(query, offset, limit)
    @Synchronized fun vector(name: String?): ImageVector? {
        if (context == null || name == null || !name.matches(Regex("[a-z0-9_]{1,80}"))) return null
        cache.get(name)?.let { return it }
        val glyph = com.kigtts.next.icons.MaterialSymbolCatalog.glyph(name) ?: return null
        val path = android.graphics.Path()
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = 24f }.getTextPath(glyph, 0, glyph.length, 0f, 24f, path)
        val bounds = android.graphics.RectF(); path.computeBounds(bounds, true)
        if (bounds.isEmpty) return null
        val scale = minOf(20f / bounds.width(), 20f / bounds.height())
        path.transform(android.graphics.Matrix().apply { setScale(scale, scale); postTranslate(12f - bounds.centerX() * scale, 12f - bounds.centerY() * scale) })
        val points = path.approximate(.035f); val nodes = mutableListOf<PathNode>()
        var previous = -1f
        for (i in points.indices step 3) {
            val fraction = points[i]
            if (i == 0 || fraction == previous) { if (nodes.isNotEmpty()) nodes += PathNode.Close; nodes += PathNode.MoveTo(points[i + 1], points[i + 2]) }
            else nodes += PathNode.LineTo(points[i + 1], points[i + 2])
            previous = fraction
        }
        nodes += PathNode.Close
        return ImageVector.Builder("MaterialSymbols.$name", 24.dp, 24.dp, 24f, 24f)
            .addPath(nodes, pathFillType = PathFillType.NonZero, fill = SolidColor(Color.Black)).build().also { cache.put(name, it) }
    }
}
fun com.kigtts.plugin.api.PluginIcon.vector(symbol: String?): ImageVector = MaterialSymbols.vector(symbol) ?: vector()
