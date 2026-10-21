package com.kigtts.next.ui.sketch

import android.graphics.Path
import androidx.compose.ui.geometry.Rect
import java.util.IdentityHashMap
import kotlin.math.*

/** Retain geometry across pan frames; offscreen strokes do not allocate paths or reach the renderer. */
internal class SketchScene {
    private class Entry(val stroke: Stroke, val bounds: Rect)
    private data class Key(val entry: Entry, val level: Int)
    private var snapshot: List<Stroke>? = null
    private var width = 0f; private var height = 0f
    private var entries = emptyList<Entry>()
    private val known = IdentityHashMap<Stroke, Entry>()
    private val paths = object : LinkedHashMap<Key, Path>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Path>?) = size > 256
    }
    internal var builtPaths = 0; private set
    fun sync(strokes: List<Stroke>, width: Float, height: Float) {
        if (snapshot === strokes && this.width == width && this.height == height) return
        if (this.width != width || this.height != height) { known.clear(); paths.clear() }
        this.width = width; this.height = height; snapshot = strokes
        val retained = IdentityHashMap<Stroke, Boolean>()
        entries = strokes.map { stroke ->
            retained[stroke] = true
            known[stroke] ?: Entry(stroke, bounds(stroke)).also { known[stroke] = it }
        }
        known.keys.removeAll { !retained.containsKey(it) }
        paths.keys.removeAll { !retained.containsKey(it.entry.stroke) }
    }
    fun draw(visible: Rect, scale: Float, action: (Stroke, Path) -> Unit) {
        val level = ceil(log2(scale.coerceAtLeast(.0000001f))).toInt().coerceIn(-24, 16)
        entries.forEach { entry -> if (entry.bounds.overlaps(visible)) {
            val key = Key(entry, level)
            val path = paths[key] ?: strokePath(simplify(entry.stroke, 2f.pow(level)), width, height).also { paths[key] = it; builtPaths++ }
            action(entry.stroke, path)
        } }
    }
    private fun bounds(stroke: Stroke): Rect {
        var left = Float.POSITIVE_INFINITY; var top = left; var right = -left; var bottom = -left
        val shorter = minOf(width, height)
        stroke.points.forEach { point ->
            val radius = point.width * shorter / 2; val x = point.position.x * width; val y = point.position.y * height
            left = minOf(left, x - radius); top = minOf(top, y - radius); right = maxOf(right, x + radius); bottom = maxOf(bottom, y + radius)
        }
        return if (left.isFinite()) Rect(left, top, right, bottom) else Rect.Zero
    }
    private fun simplify(stroke: Stroke, scale: Float): Stroke {
        if (stroke.points.size < 64) return stroke
        val threshold = .5f / scale; val shorter = minOf(width, height)
        val selected = ArrayList<StrokePoint>(); var previous = stroke.points.first(); selected += previous
        for (index in 1 until stroke.points.lastIndex) {
            val point = stroke.points[index]; val x = (point.position.x - previous.position.x) * width; val y = (point.position.y - previous.position.y) * height
            if (x * x + y * y >= threshold * threshold || abs(point.width - previous.width) * shorter * scale >= .5f) { selected += point; previous = point }
        }
        selected += stroke.points.last()
        return stroke.copy(points = selected)
    }
}
