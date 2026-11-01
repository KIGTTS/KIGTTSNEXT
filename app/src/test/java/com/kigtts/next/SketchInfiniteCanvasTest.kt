package com.kigtts.next

import android.graphics.Color
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.kigtts.next.ui.sketch.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SketchInfiniteCanvasTest {
    @Test fun newCanvasUsesDotsButRestoringExistingPaperKeepsItsStyle() {
        val state = SketchState()
        assertEquals(SketchPaperPattern.DOTS, state.pattern)
        val saved = SketchDocument(200, 400, 0xFFFFFFFFL, emptyList(), SketchPaperPattern.GRID)
        state.restore(saved)
        assertEquals(saved, state.document())
    }

    @Test fun clearingOffscreenInkResetsTheViewButKeepsThePaperAndPen() {
        val state = drawing()
        state.setPaperPattern(SketchPaperPattern.GRID)
        state.navigate(IntSize(200, 400), Offset(100f, 200f), Offset(450f, 900f), 3f)
        state.clear()
        assertTrue(state.strokes.isEmpty())
        assertEquals(1f, state.viewportZoom)
        assertEquals(Offset.Zero, state.viewportPan)
        assertEquals(SketchPaperPattern.GRID, state.pattern)
        assertEquals(0xFFFF0000L, state.color)
    }
    private fun drawing() = SketchState().apply {
        initializeCanvas(IntSize(200, 400)); color = 0xFFFF0000L; penSize = 12f
        sample(Offset(-100f, -50f), 1f, false, false, 1f, 200f, 400f)
        sample(Offset(300f, 450f), 1f, false, false, 1f, 200f, 400f); endStroke()
    }

    @Test fun pointerInputDrawsBeyondTheOldPageAndFitAllIncludesEveryBrushEdge() {
        val state = SketchState().apply { initializeCanvas(IntSize(200, 400)) }
        val size = IntSize(200, 400)
        state.navigate(size, Offset(100f, 200f), Offset(500f, 800f), 1f)
        val input = SketchInput(state)
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0L, 10L, action, 100f, 200f, 0)
            try { assertTrue(input.process(event, size, 1f)) } finally { event.recycle() }
        }
        val point = state.strokes.single().points.first().position
        assertTrue(point.x < 0f && point.y < 0f)
        val before = state.document()
        state.resetViewport()
        val viewport = state.viewport(size)
        val bounds = sketchExportBounds(before)
        val visible = androidx.compose.ui.geometry.Rect(viewport.toDocument(Offset.Zero), viewport.toDocument(Offset(200f, 400f)))
        assertTrue(visible.left <= bounds.left + .01f && visible.top <= bounds.top + .01f)
        assertTrue(visible.right >= bounds.right - .01f && visible.bottom >= bounds.bottom - .01f)
        assertEquals(before, state.document())
    }

    @Test fun unboundedCoordinatesAndPaperStyleRoundTripWhileOldDraftsRemainReadable() {
        val state = drawing(); state.setPaperPattern(SketchPaperPattern.DOTS)
        val document = state.document()
        assertEquals(document, SketchDocumentCodec.decode(SketchDocumentCodec.encode(document)))
        assertTrue(document.strokes.first().points.last().position.x > 1f)
        val old = JSONObject(SketchDocumentCodec.encode(SketchDocument(200, 400, 0xFFFFFFFFL, emptyList())))
            .put("version", 1).apply { remove("pattern") }
        assertEquals(SketchPaperPattern.PLAIN, SketchDocumentCodec.decode(old.toString()).pattern)
        val restored = SketchState().apply { restore(document) }
        assertEquals(document, restored.document())
    }

    @Test fun exportAddsProtectionToOffscreenInkAndIgnoresDistantEraserTravel() {
        val document = drawing().document().copy(pattern = SketchPaperPattern.PLAIN)
        val bounds = sketchExportBounds(document)
        assertEquals(-130f, bounds.left, .01f); assertEquals(-80f, bounds.top, .01f)
        assertEquals(330f, bounds.right, .01f); assertEquals(480f, bounds.bottom, .01f)
        val eraser = Stroke(listOf(StrokePoint(Offset(20f, 20f), .1f)), 0xFF000000L, erase = true)
        assertEquals(bounds, sketchExportBounds(document.copy(strokes = document.strokes + eraser)))
        val bitmap = renderSketchDocument(document)
        try {
            assertEquals(460, bitmap.width); assertEquals(560, bitmap.height)
            assertEquals(Color.RED, bitmap.getPixel(30, 30)); assertEquals(Color.RED, bitmap.getPixel(430, 530))
            for (x in 0 until bitmap.width) { assertEquals(Color.WHITE, bitmap.getPixel(x, 0)); assertEquals(Color.WHITE, bitmap.getPixel(x, bitmap.height - 1)) }
            for (y in 0 until bitmap.height) { assertEquals(Color.WHITE, bitmap.getPixel(0, y)); assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width - 1, y)) }
        } finally { bitmap.recycle() }
        val scaled = renderSketchDocument(document, 128)
        try { assertEquals(128, maxOf(scaled.width, scaled.height)) } finally { scaled.recycle() }
    }

    @Test fun paperPatternsExportWithConsistentSpacingAndErasersRevealThePaper() {
        val point = StrokePoint(Offset(.5f, .5f), .2f)
        for (pattern in SketchPaperPattern.entries) {
            val empty = SketchDocument(200, 400, 0xFFFFFFFFL, emptyList(), pattern)
            val original = renderSketchDocument(empty)
            try {
                assertEquals(200, original.width); assertEquals(400, original.height)
                assertEquals(Color.WHITE, original.getPixel(15, 15))
                if (pattern != SketchPaperPattern.PLAIN) assertNotEquals(Color.WHITE, original.getPixel(20, 20))
                if (pattern == SketchPaperPattern.GRID) assertNotEquals(Color.WHITE, original.getPixel(20, 25))
                if (pattern == SketchPaperPattern.DOTS) assertEquals(Color.WHITE, original.getPixel(20, 25))
            } finally { original.recycle() }
            val ink = empty.copy(strokes = listOf(Stroke(listOf(point), 0xFFFF0000L), Stroke(listOf(point), 0xFF000000L, erase = true)))
            val erased = renderSketchDocument(ink)
            try { assertNotEquals(Color.RED, erased.getPixel(erased.width / 2, erased.height / 2)) }
            finally { erased.recycle() }
        }
    }
}
