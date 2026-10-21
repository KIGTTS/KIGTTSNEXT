package com.kigtts.next

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.kigtts.next.ui.sketch.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class SketchSceneTest {
    @Test fun onlyVisibleStrokesBuildPathsAndPanFramesReuseGeometryWithoutChangingSavedInk() {
        val strokes = (0..1000).map { Stroke(listOf(StrokePoint(Offset(it * .2f, .5f), .02f)), 0xFFFF0000L) }
        val scene = SketchScene(); scene.sync(strokes, 100f, 100f)
        val visible = Rect(-5f, 0f, 105f, 100f); var drawn = 0
        scene.draw(visible, 1f) { _, _ -> drawn++ }; assertEquals(6, drawn); assertEquals(6, scene.builtPaths)
        repeat(30) { frame -> scene.sync(strokes.toList(), 100f, 100f); scene.draw(visible.translate(Offset(frame / 100f, 0f)), 1f) { _, _ -> } }
        assertEquals(6, scene.builtPaths)
        val original = strokes.first().copy(points = (0..5000).map { StrokePoint(Offset(it / 50000f, .5f), .02f) })
        scene.sync(listOf(original), 100f, 100f); scene.draw(visible, .05f) { stroke, _ -> assertSame(original, stroke) }
        assertEquals(5001, original.points.size)
    }
    @Test fun applicationRestartResetsViewportWhileRotationReentryAndThemeKeepInk() {
        val state = SketchState().apply { initializeCanvas(IntSize(200, 400)); startSession()
            sample(Offset(50f, 50f), 1f, false, false, 1f, 200f, 400f); endStroke()
            viewportZoom = 4f; viewportPan = Offset(600f, 400f) }
        val ink = state.strokes.toList(); state.initializeCanvas(IntSize(400, 200)); state.startSession()
        assertEquals(4f, state.viewportZoom, 0f); assertEquals(Offset(600f, 400f), state.viewportPan)
        val restarted = SketchState().apply { restore(state.document()); startSession() }
        assertEquals(1f, restarted.viewportZoom, 0f); assertEquals(Offset.Zero, restarted.viewportPan); assertEquals(ink, restarted.strokes.toList())
        restarted.themeBackground(0xFF151515L); assertEquals(0xFF151515L, restarted.document().background)
        restarted.themeBackground(0xFFFFFFFFL); assertEquals(0xFFFFFFFFL, restarted.document().background); assertEquals(ink, restarted.strokes.toList())
    }
}
