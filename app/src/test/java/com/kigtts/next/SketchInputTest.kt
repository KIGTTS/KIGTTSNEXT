package com.kigtts.next

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.kigtts.next.ui.sketch.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SketchInputTest {
    private val canvas = IntSize(200, 200)
    private fun event(action: Int, x: Float, y: Float, pressure: Float = 1f, buttons: Int = 0,
                      tool: Int = MotionEvent.TOOL_TYPE_STYLUS, flags: Int = 0): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply { id = 7; toolType = tool }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; this.pressure = pressure; size = 1f }
        return MotionEvent.obtain(0, 10, action, 1, arrayOf(properties), arrayOf(coords), 0, buttons, 1f, 1f, 0, 0,
            if (tool == MotionEvent.TOOL_TYPE_FINGER) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS, flags)
    }
    private fun send(input: SketchInput, action: Int, x: Float, y: Float, pressure: Float = 1f,
                     buttons: Int = 0, tool: Int = MotionEvent.TOOL_TYPE_STYLUS, flags: Int = 0) {
        val event = event(action, x, y, pressure, buttons, tool, flags)
        try { assertTrue(input.process(event, canvas, 1f)) } finally { event.recycle() }
    }
    private fun line(state: SketchState) {
        state.sample(Offset(20f, 100f), 1f, false, false, 1f, 200f, 200f)
        state.sample(Offset(180f, 100f), 1f, false, false, 1f, 200f, 200f)
        state.endStroke()
    }

    @Test fun pressureAndHistoricalSamplesChangeWidthAndAllPenTypesRender() {
        val state = SketchState().apply { penSize = 20f }
        val input = SketchInput(state)
        send(input, MotionEvent.ACTION_DOWN, 20f, 50f, .1f)
        val move = event(MotionEvent.ACTION_MOVE, 80f, 50f, .5f)
        move.addBatch(20, 150f, 50f, 1f, 1f, 0)
        try { input.process(move, canvas, 1f) } finally { move.recycle() }
        send(input, MotionEvent.ACTION_UP, 150f, 50f, 0f)
        val points = state.strokes.single().points
        assertEquals(3, points.size)
        assertTrue(points[0].width < points[1].width && points[1].width < points[2].width)
        assertTrue(points.last().width.isFinite())
        PenType.entries.forEach { type ->
            state.penType = type
            assertTrue(state.pressureWidth(.2f, true) < state.pressureWidth(.9f, true))
            assertEquals(20f, state.pressureWidth(.2f, false), .001f)
        }
        val bitmap = renderSketch(state.strokes, Color.WHITE, 200, 200)
        try {
            assertEquals(Color.WHITE, bitmap.getPixel(20, 44))
            assertNotEquals(Color.WHITE, bitmap.getPixel(150, 44))
        } finally { bitmap.recycle() }
    }

    @Test fun sideButtonTemporarilyErasesAndReleaseStartsNewInkWithoutChangingTool() {
        val state = SketchState()
        val input = SketchInput(state)
        send(input, MotionEvent.ACTION_DOWN, 10f, 80f)
        send(input, MotionEvent.ACTION_MOVE, 50f, 80f, buttons = MotionEvent.BUTTON_STYLUS_PRIMARY)
        assertTrue(state.temporaryErasing)
        assertEquals(SketchTool.PEN, state.tool)
        send(input, MotionEvent.ACTION_MOVE, 90f, 80f)
        assertFalse(state.temporaryErasing)
        send(input, MotionEvent.ACTION_UP, 90f, 80f)
        assertEquals(listOf(false, true, false), state.strokes.map { it.erase })
        assertEquals(Offset(.45f, .4f), state.strokes.last().points.first().position)
        assertEquals(SketchTool.PEN, state.tool)
    }

    @Test fun secondarySideButtonEraserTipAndKeyboardAreTemporary() {
        listOf(MotionEvent.BUTTON_STYLUS_SECONDARY to MotionEvent.TOOL_TYPE_STYLUS,
            0 to MotionEvent.TOOL_TYPE_ERASER).forEach { (buttons, tool) ->
            val state = SketchState(); val input = SketchInput(state)
            send(input, MotionEvent.ACTION_DOWN, 80f, 80f, buttons = buttons, tool = tool)
            assertTrue(state.strokes.single().erase)
            send(input, MotionEvent.ACTION_UP, 80f, 80f, tool = tool)
            assertFalse(state.temporaryErasing)
            assertEquals(SketchTool.PEN, state.tool)
        }
        val state = SketchState(); val input = SketchInput(state)
        input.keyboardEraser(true)
        send(input, MotionEvent.ACTION_DOWN, 60f, 60f)
        assertTrue(state.strokes.single().erase)
        input.keyboardEraser(false)
        send(input, MotionEvent.ACTION_MOVE, 90f, 90f)
        assertFalse(state.strokes.last().erase)
    }

    @Test fun cancellationRestoresInkAndWholeStrokeErasure() {
        val state = SketchState(); line(state)
        val original = state.strokes.toList()
        val input = SketchInput(state)
        send(input, MotionEvent.ACTION_DOWN, 80f, 40f)
        send(input, MotionEvent.ACTION_CANCEL, 80f, 40f)
        assertEquals(original, state.strokes.toList())
        state.tool = SketchTool.ERASER; state.eraserType = EraserType.STROKE
        send(input, MotionEvent.ACTION_DOWN, 100f, 100f)
        assertTrue(state.strokes.isEmpty())
        send(input, MotionEvent.ACTION_CANCEL, 100f, 100f)
        assertEquals(original, state.strokes.toList())
    }

    @Test fun canceledFingerUpRollsBackUnwantedPalmStroke() {
        val state = SketchState(); val input = SketchInput(state)
        send(input, MotionEvent.ACTION_DOWN, 20f, 20f, tool = MotionEvent.TOOL_TYPE_FINGER)
        send(input, MotionEvent.ACTION_UP, 20f, 20f, tool = MotionEvent.TOOL_TYPE_FINGER, flags = MotionEvent.FLAG_CANCELED)
        assertTrue(state.strokes.isEmpty())
    }

    @Test fun localEraserAndExportClearOnlyCoveredInkAndAllowDrawingAgain() {
        val state = SketchState().apply { color = 0xFFFF0000; penSize = 12f }
        line(state)
        state.sample(Offset(100f, 80f), 1f, false, true, 1f, 200f, 200f)
        state.sample(Offset(100f, 120f), 1f, false, true, 1f, 200f, 200f)
        state.endStroke()
        val bitmap = renderSketch(state.strokes, Color.WHITE, 200, 200)
        try {
            assertEquals(Color.WHITE, bitmap.getPixel(100, 100))
            assertEquals(Color.RED, bitmap.getPixel(50, 100))
            assertEquals(Color.RED, bitmap.getPixel(150, 100))
            assertEquals(Color.WHITE, bitmap.getPixel(100, 50))
        } finally { bitmap.recycle() }
        state.color = 0xFF0000FF
        state.sample(Offset(100f, 100f), 1f, false, false, 1f, 200f, 200f); state.endStroke()
        val redrawn = renderSketch(state.strokes, Color.WHITE, 200, 200)
        try { assertEquals(Color.BLUE, redrawn.getPixel(100, 100)) } finally { redrawn.recycle() }
    }

    @Test fun wholeStrokeEraserHitsBetweenSparseSamplesAndAlongFastMovement() {
        val state = SketchState(); line(state)
        state.eraserType = EraserType.STROKE; state.eraserSize = 8f
        state.sample(Offset(100f, 20f), 1f, false, true, 1f, 200f, 200f)
        assertEquals(1, state.strokes.size)
        state.sample(Offset(100f, 180f), 1f, false, true, 1f, 200f, 200f)
        assertTrue(state.strokes.isEmpty())
    }
}
