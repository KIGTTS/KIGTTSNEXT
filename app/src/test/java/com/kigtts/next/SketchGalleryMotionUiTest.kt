package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import com.kigtts.next.theme.*
import com.kigtts.next.ui.sketch.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SketchGalleryMotionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun gallerySlidesBothWaysAndKeepsTheDrawingAndViewportMounted() {
        val state = SketchState().apply {
            initializeCanvas(IntSize(1179, 2556)); startSession()
            sample(Offset(100f, 100f), 1f, false, false, 1f, 1179f, 2556f); endStroke()
            viewportZoom = 2f; viewportPan = Offset(30f, 40f)
        }
        val ink = state.strokes.toList()
        compose.setContent { KigttsTheme(ThemeSettings()) { SketchPageScaffold({}, {}) { SketchScreen(state, 0, {}, {}) } } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("sketch-album-open").performClick()
        compose.mainClock.advanceTimeBy(96); compose.waitForIdle()
        val entering = compose.onNodeWithTag("sketch-gallery-page").fetchSemanticsNode().boundsInRoot
        assertTrue("Enter must have an intermediate horizontal position: $entering", entering.left > 0f)
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        compose.onNodeWithTag("sketch-canvas").assertExists()
        compose.onNodeWithContentDescription("返回画板").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("返回画板").performClick()
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
        val moving = compose.onNodeWithTag("sketch-gallery-page").fetchSemanticsNode().boundsInRoot
        assertTrue("Return must have an intermediate horizontal position: $moving", moving.left > 0f)
        compose.onNodeWithTag("sketch-canvas").assertExists()
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        compose.onNodeWithTag("sketch-gallery-motion").assertDoesNotExist()
        compose.runOnIdle { assertEquals(ink, state.strokes.toList()); assertEquals(2f, state.viewportZoom, 0f); assertEquals(Offset(30f, 40f), state.viewportPan) }
        compose.mainClock.autoAdvance = true
    }
}
