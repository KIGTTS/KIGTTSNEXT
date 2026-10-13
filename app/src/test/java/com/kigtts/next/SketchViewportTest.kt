package com.kigtts.next

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SketchViewportTest {
    @Test fun rotationFitsTheOriginalDocumentWithoutStretchingOrDiscardingItsInkAndDraftRoundTrips() {
        val state = SketchState()
        state.initializeCanvas(IntSize(200, 400), 0xFFEEEEFFL)
        state.sample(Offset(50f, 100f), .8f, true, false, 1f, 200f, 400f); state.endStroke()
        val original = state.document()
        state.initializeCanvas(IntSize(400, 200))
        val viewport = state.viewport(IntSize(400, 200))
        assertEquals(IntSize(200, 400), state.documentSize)
        assertEquals(.5f, viewport.scale, .0001f)
        assertEquals(Offset(50f, 100f), viewport.toDocument(Offset(175f, 50f)))
        assertEquals(original, state.document())
        val restored = SketchState().apply { restore(SketchDocumentCodec.decode(SketchDocumentCodec.encode(original))) }
        assertEquals(original, restored.document())
        assertEquals(original, restored.apply { initializeCanvas(IntSize(900, 450)) }.document())
    }

    @Test fun twoFingerNavigationKeepsItsAnchorAndDrawingUsesTheInverseOfTheVisibleViewport() {
        val state = SketchState().apply { initializeCanvas(IntSize(200, 400)) }
        val window = IntSize(400, 200)
        val before = state.viewport(window).toDocument(Offset(175f, 50f))
        state.navigate(window, Offset(175f, 50f), Offset(210f, 80f), 2f)
        val transformed = state.viewport(window)
        assertEquals(before.x, transformed.toDocument(Offset(210f, 80f)).x, .001f)
        assertEquals(before.y, transformed.toDocument(Offset(210f, 80f)).y, .001f)
        val input = SketchInput(state)
        touch(input, window, MotionEvent.ACTION_DOWN, listOf(Offset(210f, 80f)))
        touch(input, window, MotionEvent.ACTION_UP, listOf(Offset(210f, 80f)))
        assertEquals(Offset(.25f, .25f), state.strokes.single().points.first().position)
    }

    @Test fun aSecondFingerRollsBackBothNewInkAndTentativeWholeStrokeErasureUntilAllFingersLift() {
        listOf(SketchTool.PEN, SketchTool.ERASER).forEach { tool ->
            val state = SketchState().apply { initializeCanvas(IntSize(200, 200)) }
            state.sample(Offset(50f, 100f), 1f, false, false, 1f, 200f, 200f)
            state.sample(Offset(150f, 100f), 1f, false, false, 1f, 200f, 200f); state.endStroke()
            val original = state.strokes.toList()
            state.tool = tool; state.eraserType = EraserType.STROKE
            val input = SketchInput(state); val window = IntSize(200, 200)
            touch(input, window, MotionEvent.ACTION_DOWN, listOf(Offset(80f, 100f)))
            touch(input, window, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                listOf(Offset(80f, 100f), Offset(120f, 100f)))
            assertEquals(original, state.strokes.toList())
            touch(input, window, MotionEvent.ACTION_MOVE, listOf(Offset(40f, 120f), Offset(160f, 120f)))
            assertEquals(3f, state.viewportZoom, .001f)
            touch(input, window, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                listOf(Offset(40f, 120f), Offset(160f, 120f)))
            touch(input, window, MotionEvent.ACTION_MOVE, listOf(Offset(50f, 130f)))
            touch(input, window, MotionEvent.ACTION_UP, listOf(Offset(50f, 130f)))
            assertEquals(original, state.strokes.toList())
            assertFalse(state.temporaryErasing)
        }
    }

    private fun touch(input: SketchInput, size: IntSize, action: Int, points: List<Offset>) {
        val properties = points.indices.map { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coordinates = points.map { point -> MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = 1f } }.toTypedArray()
        val event = MotionEvent.obtain(0, 20, action, points.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue(input.process(event, size, 1f)) } finally { event.recycle() }
    }
}
