package com.kigtts.next.ui.cards.camera

import com.github.takahirom.roborazzi.captureRoboImage
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
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
class CameraNativeControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun assertCircle(tag: String, minimum: Float) {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.width, bounds.height, 1f)
        assertTrue(bounds.width >= minimum - 1f)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
    }

    @Test fun portraitPresetsRespectHardwareAndDraggingOpensAContinuousDial() {
        var ratio by mutableFloatStateOf(1f); var changes = 0
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraZoomRail(ratio, .6f, 4f, true, { ratio = it; changes++ }, false,
                Modifier.size(360.dp, 640.dp), androidx.compose.ui.geometry.Rect(20f, 550f, 340f, 598f))
        } }
        val button = compose.onNodeWithTag("camera-zoom-stop/0.6").fetchSemanticsNode().boundsInRoot
        assertEquals("Presets remain circular in the real 48dp rail", button.width, button.height, 1f)
        compose.onNodeWithTag("camera-zoom-slider").assertDoesNotExist()
        compose.onNodeWithTag("camera-zoom-stop/0.6").performClick()
        assertEquals(.6f, ratio, .001f)
        compose.onNodeWithTag("camera-zoom-stop/1").performClick()
        compose.mainClock.autoAdvance = false
        val start = compose.onNodeWithTag("camera-zoom-stop/1").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start); moveTo(start + androidx.compose.ui.geometry.Offset(100f, 0f), 120) }
        compose.mainClock.advanceTimeBy(180); compose.waitForIdle()
        compose.onNodeWithTag("camera-zoom-dial").assertIsDisplayed()
        assertTrue("Zoom must update before release", ratio > 1f && changes > 2)
        compose.onRoot().captureRoboImage("build/previews/camera-zoom-dial-portrait.png")
        val current = ratio
        compose.onRoot().performTouchInput { moveTo(start + androidx.compose.ui.geometry.Offset(200f, 0f), 120) }
        assertTrue(ratio > current)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1100); compose.waitForIdle()
        compose.onNodeWithTag("camera-zoom-dial").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("camera-zoom-stop/2").performClick(); assertEquals(2f, ratio, .001f)
    }

    @Test @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
    fun landscapePresetsAndWheelHaveTheSameUsableReportedRange() {
        var ratio by mutableFloatStateOf(1.2f)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraZoomRail(ratio, 1.2f, 4f, true, { ratio = it }, true, Modifier.size(700.dp, 300.dp),
                androidx.compose.ui.geometry.Rect(630f, 30f, 686f, 270f))
        } }
        compose.onNodeWithTag("camera-zoom-stop/1").assertDoesNotExist()
        compose.onNodeWithTag("camera-zoom-stop/1.2").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        val start = compose.onNodeWithTag("camera-zoom-stop/2").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start); moveTo(start - androidx.compose.ui.geometry.Offset(0f, 100f), 120) }
        compose.mainClock.advanceTimeBy(180); compose.waitForIdle()
        compose.onNodeWithTag("camera-zoom-dial").assertIsDisplayed()
        assertTrue(ratio > 1.2f)
        compose.onRoot().captureRoboImage("build/previews/camera-zoom-dial-landscape.png")
        compose.onRoot().performTouchInput { up() }
        compose.onNodeWithTag("camera-zoom-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(cameraZoomPosition(3f, 1.2f..4f)) }
        assertEquals(3f, ratio, .001f)
        compose.mainClock.autoAdvance = true
    }
}
