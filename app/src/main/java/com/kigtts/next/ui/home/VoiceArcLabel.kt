package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
import java.text.BreakIterator
import kotlin.math.*

/** Place each glyph and the icon on the capsule's centerline, with the same local tangent. */
@Composable internal fun VoiceArcLabel(label: String, icon: ImageVector, showLabel: Boolean,
    pose: VoiceArcPose, landscape: Boolean) {
    val glyphs = remember(label, showLabel) {
        if (!showLabel) emptyList() else {
            val iterator = BreakIterator.getCharacterInstance().apply { setText(label) }
            buildList { var start = iterator.first(); var end = iterator.next()
                while (end != BreakIterator.DONE) { add(label.substring(start, end)); start = end; end = iterator.next() }
            }
        }
    }
    Layout(modifier = Modifier.clearAndSetSemantics { }, content = {
        Icon(icon, null, Modifier.size(20.dp))
        glyphs.forEach { Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
        if (showLabel) Text("…", style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }) { measurables, constraints ->
        val items = measurables.map { it.measure(Constraints()) }
        val gap = if (showLabel) 4.dp.toPx() else 0f
        val limit = ((pose.radius * (pose.end - pose.start) + pose.thickness - 16f) * density).coerceAtLeast(items[0].width.toFloat())
        val selected = mutableListOf(0)
        var used = items[0].width.toFloat() + gap
        val last = items.lastIndex
        for (index in 1..glyphs.size) {
            val remaining = if (index < glyphs.size) items[last].width else 0
            if (used + items[index].width + remaining > limit) { selected += last; used += items[last].width; break }
            selected += index; used += items[index].width
        }
        val total = selected.sumOf { items[it].width }.toFloat() + if (selected.size > 1) gap else 0f
        layout(constraints.maxWidth, constraints.maxHeight) {
            var cursor = -total / 2f
            selected.forEachIndexed { order, index ->
                if (order == 1) cursor += gap
                val item = items[index]
                val along = cursor + item.width / 2f
                val angle = (pose.start + pose.end) / 2f + along / (pose.radius * density)
                val point = pose.center + Offset(pose.radius * cos(angle), pose.radius * sin(angle)) - pose.bounds.topLeft
                val x = (if (landscape) point.y else point.x) * density
                val y = (if (landscape) pose.bounds.width - point.x else point.y) * density
                item.placeWithLayer((x - item.width / 2f).roundToInt(), (y - item.height / 2f).roundToInt()) {
                    rotationZ = angle * 180f / PI.toFloat() + 90f - if (landscape) 90f else 0f
                }
                cursor += item.width
            }
        }
    }
}
