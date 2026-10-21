package com.kigtts.next.ui.sketch

import android.os.Build
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/** Tracks a pointer ID (not its changing index) and rolls back canceled input/palm touches. */
class SketchInput(private val state: SketchState) {
    private var pointerId: Int? = null
    private var activeTool = MotionEvent.TOOL_TYPE_UNKNOWN
    private var snapshot: List<Stroke> = emptyList()
    private var lastPoint = Offset.Zero
    private var lastPressure = 1f
    private var lastErase = false
    private var navigating = false
    private var gestureCenter = Offset.Zero
    private var gestureDistance = 0f
    var keyboardEraser = false
        private set

    fun keyboardEraser(held: Boolean) { keyboardEraser = held; state.temporaryErasing = held }

    fun process(event: MotionEvent, canvas: IntSize, density: Float): Boolean {
        if (canvas.width <= 0 || canvas.height <= 0) return false
        state.initializeCanvas(canvas)
        val fingers = (0 until event.pointerCount).filter { event.getToolType(it) == MotionEvent.TOOL_TYPE_FINGER }
        if (navigating || (fingers.size >= 2 && (pointerId == null || activeTool == MotionEvent.TOOL_TYPE_FINGER))) {
            if (!navigating) { cancel(); navigating = true; gestureDistance = 0f }
            if (fingers.size >= 2 && event.actionMasked != MotionEvent.ACTION_POINTER_UP) {
                val first = Offset(event.getX(fingers[0]), event.getY(fingers[0]))
                val second = Offset(event.getX(fingers[1]), event.getY(fingers[1]))
                val center = (first + second) / 2f; val distance = (first - second).getDistance()
                if (event.actionMasked == MotionEvent.ACTION_MOVE && gestureDistance > 0f && distance > 0f)
                    state.navigate(canvas, gestureCenter, center, distance / gestureDistance)
                gestureCenter = center; gestureDistance = distance
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                navigating = false; gestureDistance = 0f
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                begin(event, event.actionIndex)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val tool = event.getToolType(event.actionIndex)
                if (activeTool == MotionEvent.TOOL_TYPE_FINGER &&
                    (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER)) {
                    cancel()
                    begin(event, event.actionIndex)
                } else return pointerId != null
            }
            MotionEvent.ACTION_CANCEL -> { cancel(); return true }
        }
        val id = pointerId ?: return false
        val index = event.findPointerIndex(id)
        if (index < 0) { cancel(); return false }
        val isUp = event.actionMasked == MotionEvent.ACTION_UP ||
            (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.actionIndex) == id)
        if (isUp && Build.VERSION.SDK_INT >= 33 && event.flags and MotionEvent.FLAG_CANCELED != 0) {
            cancel(); return true
        }
        val stylus = activeTool == MotionEvent.TOOL_TYPE_STYLUS || activeTool == MotionEvent.TOOL_TYPE_ERASER
        val sideButton = stylus && event.buttonState and
            (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
        val temporary = keyboardEraser || sideButton || activeTool == MotionEvent.TOOL_TYPE_ERASER
        state.temporaryErasing = temporary
        val erase = state.tool == SketchTool.ERASER || temporary
        state.eraserCursor = if (erase && !isUp) Offset(event.getX(index), event.getY(index)) else null
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
            event.actionMasked == MotionEvent.ACTION_MOVE || isUp) {
            for (history in 0 until event.historySize) {
                state.sample(state.viewport(canvas).toDocument(Offset(event.getHistoricalX(index, history), event.getHistoricalY(index, history))),
                    event.getHistoricalPressure(index, history), stylus, erase, density,
                    state.documentSize.width.toFloat(), state.documentSize.height.toFloat())
            }
            val point = Offset(event.getX(index), event.getY(index))
            // Up commonly reports zero pressure; keep the previous width and tool for a final moved point.
            if (!isUp || (point - lastPoint).getDistance() > .5f) state.sample(state.viewport(canvas).toDocument(point),
                if (isUp) lastPressure else event.getPressure(index), stylus, if (isUp) lastErase else erase,
                density, state.documentSize.width.toFloat(), state.documentSize.height.toFloat())
            lastPoint = point; lastPressure = event.getPressure(index); lastErase = erase
        }
        if (isUp) { state.endStroke(); pointerId = null; snapshot = emptyList(); state.temporaryErasing = keyboardEraser }
        return true
    }

    private fun begin(event: MotionEvent, index: Int) {
        state.endStroke()
        pointerId = event.getPointerId(index)
        activeTool = event.getToolType(index)
        snapshot = state.strokes.toList()
        lastPoint = Offset(event.getX(index), event.getY(index))
    }

    fun release() { cancel(); navigating = false; gestureDistance = 0f; keyboardEraser(false) }

    private fun cancel() {
        state.eraserCursor = null
        if (pointerId != null) { state.strokes.clear(); state.strokes.addAll(snapshot) }
        pointerId = null
        snapshot = emptyList()
        state.endStroke()
        state.temporaryErasing = keyboardEraser
    }
}
