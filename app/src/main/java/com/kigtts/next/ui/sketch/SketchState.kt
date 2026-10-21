package com.kigtts.next.ui.sketch

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.pow

enum class SketchTool { PEN, ERASER }
enum class PenType(val label: String) { ROUND("圆笔"), FOUNTAIN("钢笔"), HIGHLIGHTER("荧光笔") }
enum class EraserType(val label: String) { PIXEL("局部擦除"), STROKE("整笔擦除") }

/** Coordinates use the original reference size; they may extend beyond every edge, including negative values. */
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
    var documentSize by mutableStateOf(IntSize.Zero)
        private set
    var background by mutableStateOf(0xFFFFFFFFL)
        private set
    var pattern by mutableStateOf(SketchPaperPattern.PLAIN)
        private set
    var viewportZoom by mutableFloatStateOf(1f)
        internal set
    var viewportPan by mutableStateOf(Offset.Zero)
        internal set
    var revision by mutableLongStateOf(0L)
        private set
    private var activeStroke: Stroke? = null
    private var previousErasePoint: Offset? = null
    private var sessionStarted = false
    fun startSession() { if (!sessionStarted) { viewportZoom = 1f; viewportPan = Offset.Zero; sessionStarted = true } }
    fun themeBackground(color: Long) { if (background != color) { background = color; revision++ } }

    fun pressureWidth(pressure: Float, stylus: Boolean): Float {
        if (!stylus) return penSize
        val value = if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 1f
        val response = if (penType == PenType.FOUNTAIN) value.pow(1.4f) else value
        return penSize * (.15f + .85f * response)
    }

    fun sample(point: Offset, pressure: Float, stylus: Boolean, erase: Boolean,
               density: Float, width: Float, height: Float) {
        val shorter = minOf(width, height).coerceAtLeast(1f)
        if (!point.x.isFinite() || !point.y.isFinite()) return
        val normalized = Offset(point.x / width.coerceAtLeast(1f), point.y / height.coerceAtLeast(1f))
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

    fun initializeCanvas(size: IntSize, color: Long = background) {
        if (documentSize.width > 0 || size.width <= 0 || size.height <= 0) return
        documentSize = size; background = color; revision++
    }

    fun document(): SketchDocument = SketchDocument(documentSize.width.coerceAtLeast(1),
        documentSize.height.coerceAtLeast(1), background, strokes.toList(), pattern)

    fun restore(document: SketchDocument) {
        endStroke(); strokes.clear(); strokes.addAll(document.strokes)
        documentSize = IntSize(document.width, document.height); background = document.background
        pattern = document.pattern
        resetViewport(); revision++
    }

    fun resetViewport() {
        val bounds = sketchExportBounds(document())
        viewportZoom = minOf(documentSize.width / bounds.width, documentSize.height / bounds.height, 1f).coerceAtLeast(.00001f)
        viewportPan = Offset(documentSize.width / 2f, documentSize.height / 2f) - bounds.center
    }
    fun setPaperPattern(value: SketchPaperPattern) { if (pattern != value) { pattern = value; revision++ } }
    internal fun viewport(window: IntSize) = SketchViewport(documentSize, window, viewportZoom, viewportPan)

    internal fun navigate(window: IntSize, previousCenter: Offset, center: Offset, factor: Float) {
        val old = viewport(window)
        val anchor = old.toDocument(previousCenter)
        if (!factor.isFinite() || factor <= 0f) return
        viewportZoom = (viewportZoom * factor).coerceIn(.00001f, 16f)
        val next = viewport(window)
        viewportPan = (center - Offset(window.width / 2f, window.height / 2f)) / next.scale -
            (anchor - Offset(documentSize.width / 2f, documentSize.height / 2f))
    }

    fun endStroke() {
        if (activeStroke != null || previousErasePoint != null) revision++
        activeStroke = null; previousErasePoint = null
    }
    fun clear() { endStroke(); strokes.clear(); revision++ }
}
