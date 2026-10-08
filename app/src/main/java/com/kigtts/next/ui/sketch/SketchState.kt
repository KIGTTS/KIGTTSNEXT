package com.kigtts.next.ui.sketch

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import kotlin.math.pow

enum class SketchTool { PEN, ERASER }
enum class PenType(val label: String) { ROUND("圆笔"), FOUNTAIN("钢笔"), HIGHLIGHTER("荧光笔") }
enum class EraserType(val label: String) { PIXEL("局部擦除"), STROKE("整笔擦除") }

/** Position is normalized to the canvas; width is relative to its shorter edge. */
data class StrokePoint(val position: Offset, val width: Float)
data class Stroke(val points: List<StrokePoint>, val color: Long, val alpha: Float = 1f, val erase: Boolean = false)

class SketchState {
    val strokes = mutableStateListOf<Stroke>()
    var color by mutableStateOf(0xFF65D7DE)
    var tool by mutableStateOf(SketchTool.PEN)
    var penType by mutableStateOf(PenType.ROUND)
    var eraserType by mutableStateOf(EraserType.PIXEL)
    var penSize by mutableFloatStateOf(3f)
    var eraserSize by mutableFloatStateOf(24f)
    var temporaryErasing by mutableStateOf(false)
        internal set
    private var activeStroke: Stroke? = null
    private var previousErasePoint: Offset? = null

    fun pressureWidth(pressure: Float, stylus: Boolean): Float {
        if (!stylus) return penSize
        val value = if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 1f
        val response = if (penType == PenType.FOUNTAIN) value.pow(1.4f) else value
        return penSize * (.15f + .85f * response)
    }

    fun sample(point: Offset, pressure: Float, stylus: Boolean, erase: Boolean,
               density: Float, width: Float, height: Float) {
        val shorter = minOf(width, height).coerceAtLeast(1f)
        val normalized = Offset((point.x / width.coerceAtLeast(1f)).coerceIn(0f, 1f),
            (point.y / height.coerceAtLeast(1f)).coerceIn(0f, 1f))
        if (erase && eraserType == EraserType.STROKE) {
            activeStroke = null
            val radius = eraserSize * density / 2f
            val previous = previousErasePoint ?: point
            strokes.removeAll { !it.erase && strokeIntersects(it, previous, point, radius, width, height) }
            previousErasePoint = point
            return
        }
        previousErasePoint = null
        val size = if (erase) eraserSize else pressureWidth(pressure, stylus)
        val sample = StrokePoint(normalized, size * density / shorter)
        val previous = activeStroke
        if (previous == null || previous.erase != erase) {
            val stroke = Stroke(listOf(sample), color, if (!erase && penType == PenType.HIGHLIGHTER) .4f else 1f, erase)
            strokes.add(stroke)
            activeStroke = stroke
        } else {
            val index = strokes.indexOfLast { it === previous }
            if (index < 0) { endStroke(); sample(point, pressure, stylus, erase, density, width, height); return }
            val stroke = previous.copy(points = previous.points + sample)
            strokes[index] = stroke
            activeStroke = stroke
        }
    }

    fun endStroke() { activeStroke = null; previousErasePoint = null }
    fun clear() { endStroke(); strokes.clear() }
}
