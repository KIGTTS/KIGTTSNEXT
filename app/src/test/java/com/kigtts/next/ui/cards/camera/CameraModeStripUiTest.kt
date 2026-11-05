package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
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
class CameraModeStripUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun centered(mode: CameraMode, vertical: Boolean = false) {
        val strip = compose.onNodeWithTag("camera-mode-strip").fetchSemanticsNode().boundsInRoot
        val selected = compose.onNodeWithTag("camera-mode/${mode.name}").fetchSemanticsNode().boundsInRoot
        assertEquals(if (vertical) strip.center.y else strip.center.x,
            if (vertical) selected.center.y else selected.center.x, 2f)
    }

    @Test fun swipeFollowsFingerAndCommitsOneModeOnReleaseThenCollapsesWithoutLosingSelection() {
        var mode by mutableStateOf(CameraMode.PHOTO)
        val changes = mutableListOf<CameraMode>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraModeStrip(mode, { mode = it; changes += it }, true, false)
        } }
        compose.mainClock.autoAdvance = false
        centered(mode)
        val before = compose.onNodeWithTag("camera-mode/PHOTO").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("camera-mode-strip").performTouchInput { down(center); moveBy(Offset(-260f, 0f), 300) }
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        val during = compose.onNodeWithTag("camera-mode/PHOTO").fetchSemanticsNode().boundsInRoot.center
        assertTrue(during.x < before.x - 40f)
        assertTrue(changes.isEmpty())
        compose.onNodeWithTag("camera-mode-strip").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
        assertEquals(listOf(CameraMode.TIME_LAPSE), changes)
        centered(mode)
        compose.mainClock.advanceTimeBy(3_000); compose.waitForIdle()
        compose.onNodeWithTag("camera-mode-strip").assertWidthIsEqualTo(104.dp)
        centered(mode)
        compose.onRoot().captureRoboImage("build/previews/camera-mode-capsule.png")
        compose.onNodeWithTag("camera-mode-strip").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(260); compose.waitForIdle()
        compose.onNodeWithTag("camera-mode-strip").assertWidthIsAtLeast(360.dp)
        compose.onNodeWithTag("camera-mode-strip").performTouchInput { moveBy(Offset(240f, 0f), 400); up() }
        compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
        assertEquals(CameraMode.PHOTO, mode)
        centered(mode)
        compose.mainClock.autoAdvance = true
    }

    @Test fun firstAndLastModeAndDockMovesKeepSelectedModeCentered() {
        var mode by mutableStateOf(CameraMode.SCAN)
        var overlay by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            val controls = rememberCameraControlsContent { vertical ->
                CameraModeControls(mode, { mode = it }, true, vertical, false, 0) { Spacer(Modifier.height(80.dp)) }
            }
            CameraPageLayout(preview = { modifier, _ -> Box(modifier) }, controls = controls,
                photoAspect = if (overlay) .48f else .75f)
        } }
        centered(mode)
        compose.runOnIdle { mode = CameraMode.TIME_LAPSE }
        compose.waitForIdle(); centered(mode)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3_000); compose.waitForIdle()
        compose.onNodeWithTag("camera-mode-strip").assertWidthIsEqualTo(104.dp)
        compose.runOnIdle { overlay = true }
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        compose.onNodeWithTag("camera-mode-strip").assertWidthIsEqualTo(104.dp)
        centered(mode)
        compose.mainClock.autoAdvance = true
    }

    @Test @Config(sdk = [35], qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
    fun landscapeSwipesVerticallyAndCentersTheRotatedLabel() {
        var mode by mutableStateOf(CameraMode.PHOTO)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraModeStrip(mode, { mode = it }, true, true)
        } }
        centered(mode, true)
        compose.onNodeWithTag("camera-mode-strip").performTouchInput { swipeUp(durationMillis = 500) }
        compose.waitForIdle()
        assertEquals(CameraMode.TIME_LAPSE, mode)
        centered(mode, true)
        compose.onRoot().captureRoboImage("build/previews/camera-mode-landscape.png")
    }
}
