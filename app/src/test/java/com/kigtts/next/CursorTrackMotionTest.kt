package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.components.LocalMultiWindowMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class CursorTrackMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun step(ms: Long) { repeat(((ms + 15) / 16).toInt()) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } }
    private fun pixels(name: String): IntArray {
        val path = "build/previews/cursor-motion-$name.png"
        compose.onNodeWithTag("editor-footer").captureRoboImage(path)
        val bitmap = BitmapFactory.decodeFile(path)
        return IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height); bitmap.recycle()
        }
    }
    @Test fun smallWindowKeepsTheCursorStripInlineWhileTheOuterSurfaceOwnsSafeArea() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app)
        model.setAutoPlayback(false)
        var small by mutableStateOf(true)
        compose.setContent { CompositionLocalProvider(LocalMultiWindowMode provides small) { KigttsApp(model) } }
        compose.runOnIdle { model.text("abcdef中文"); model.editing(true) }
        compose.waitForIdle()
        val strip = compose.onNodeWithTag("cursor-track").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("editor-footer-actions").fetchSemanticsNode().boundsInRoot
        assertTrue("The cursor track stays in the footer row", strip.center.y in actions.top..actions.bottom)
        assertTrue("The small-window track keeps the inline width", strip.width < actions.width * .65f)
        compose.onNodeWithTag("speech-editor").performTextInputSelection(androidx.compose.ui.text.TextRange(8))
        // Cross the device's touch slop, then move at least two 12 dp cursor steps.
        compose.onNodeWithTag("cursor-track").performTouchInput { swipeLeft(startX = centerX, endX = centerX - 180f, durationMillis = 300) }
        compose.waitForIdle()
        compose.onNodeWithTag("editor-footer").captureRoboImage("build/previews/cursor-small-window.png")
        assertTrue("Wide cursor drag: ${model.state.value.editorValue}, strip=$strip, actions=$actions", model.state.value.editorValue.selection.start < 8)
        compose.runOnIdle { small = false }
        val inline = compose.onNodeWithTag("cursor-track").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("editor-footer-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(inline.center.y in row.top..row.bottom)
        assertEquals(strip.width, inline.width, 1f)
    }
    @Test fun enteringAndLeavingAnimateTheStripAndSideSpacingWithoutChangingHeightOrSelection() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "interaction", "quick-phrases").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        val model = testAppModel(app)
        compose.setContent { KigttsApp(model) }
        val footer = compose.onNodeWithTag("editor-footer")
        val bounds = footer.getUnclippedBoundsInRoot()
        val closed = pixels("closed")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.text("abcdef中文"); model.editing(true) }; step(64)
        val entering = pixels("enter-mid"); step(500)
        val opened = pixels("open")
        assertFalse(closed.contentEquals(entering)); assertFalse(opened.contentEquals(entering))
        val openBounds = footer.getUnclippedBoundsInRoot()
        assertEquals((bounds.bottom - bounds.top).value, (openBounds.bottom - openBounds.top).value, .01f)
        compose.onNodeWithContentDescription("移动光标").assertExists()
        val selection = model.state.value.editorValue.selection
        compose.runOnIdle { model.editing(false) }; step(48)
        compose.onNodeWithContentDescription("移动光标").assertDoesNotExist()
        val leaving = pixels("exit-mid"); step(500)
        assertFalse(leaving.contentEquals(pixels("closed-again")))
        compose.onNodeWithTag("cursor-track").assertDoesNotExist()
        assertEquals(selection, model.state.value.editorValue.selection)
        repeat(3) { compose.runOnIdle { model.editing(true) }; step(32); compose.runOnIdle { model.editing(false) }; step(32) }
        step(500); compose.onNodeWithTag("cursor-track").assertDoesNotExist()
        val finalBounds = footer.getUnclippedBoundsInRoot()
        assertEquals((bounds.bottom - bounds.top).value, (finalBounds.bottom - finalBounds.top).value, .01f)
    }
}
