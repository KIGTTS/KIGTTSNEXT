package com.kigtts.next.ui.home

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.SubtitleAlignment
import com.kigtts.next.ui.components.MaterialSymbols

@Composable
internal fun AlignmentPreview(alignment: SubtitleAlignment) {
    val tint = LocalContentColor.current
    Box(Modifier.size(36.dp).drawWithCache {
        val glyph = alignmentSegment(alignment)
        onDrawBehind {
            scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(glyph, tint)
            }
        }
    })
}

/** Rearranges the three original Material Symbols Rounded Segment strokes. */
private fun alignmentSegment(alignment: SubtitleAlignment): androidx.compose.ui.graphics.Path {
    val contours = MaterialSymbols.androidPath("segment")?.let { segment ->
        val measure = PathMeasure(segment, false)
        buildList {
            do {
                val path = Path()
                if (measure.getSegment(0f, measure.length, path, true)) {
                    path.close()
                    add(path to RectF().also { path.computeBounds(it, true) })
                }
            } while (measure.nextContour())
        }.takeIf { it.size == 3 }
    }
    val result = Path()
    for (row in 0..2) {
        val isLong = row == alignment.row
        val source = if (isLong) contours?.maxByOrNull { it.second.width() }
            else contours?.minByOrNull { it.second.width() }
        val width = source?.second?.width() ?: if (isLong) 20f else 40f / 3f
        val left = 2f + (20f - width) * alignment.column / 2f
        val cy = 12f + (row - 1) * (50f / 9f)
        if (source == null) {
            // Same rounded Segment proportions if the offline icon font is not initialized.
            val radius = 10f / 9f
            result.addRoundRect(left, cy - radius, left + width, cy + radius, radius, radius, Path.Direction.CW)
        } else {
            val (path, bounds) = source
            result.addPath(path, left - bounds.left, cy - bounds.centerY())
        }
    }
    return result.asComposePath()
}
