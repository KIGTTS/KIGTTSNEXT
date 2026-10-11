package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.KigttsApp
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
