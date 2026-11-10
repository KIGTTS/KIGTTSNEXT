package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.util.concurrent.Futures
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class CameraProUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun globalExposureSitsLeftOfZoomAndTheRulerDoesNotEraseFocusOffset() {
        val exposure = CameraExposureCompensation().apply { attach(-9..12, 1f / 3f); chooseFocusOffset(2) }
        var expanded by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(360.dp, 240.dp)) {
                val rail = Rect(0f, 192f, 360f, 240f)
                if (!expanded) CameraZoomRail(1f, .6f, 8f, true, {}, false, Modifier.matchParentSize(), rail)
                CameraExposureRail(exposure, rail, false, 4, expanded, true, { expanded = !expanded }, exposure::chooseBaseline)
            }
        } }
        val button = compose.onNodeWithTag("camera-global-exposure")
        assertTrue(button.getUnclippedBoundsInRoot().right <=
            compose.onNodeWithTag("camera-zoom-stop/0.6").getUnclippedBoundsInRoot().left)
        button.performClick()
        compose.onNodeWithTag("camera-global-exposure-slider").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        compose.runOnIdle { assertEquals(5, exposure.index) }
        compose.onNodeWithTag("camera-exposure-ruler").captureRoboImage("build/previews/camera-exposure-ruler.png")
        compose.onNodeWithTag("camera-global-exposure-reset").performClick()
        compose.runOnIdle { assertEquals(2, exposure.index) }
        compose.onNodeWithTag("camera-global-exposure-close").performClick()
        button.assertIsDisplayed()
    }

    @Test fun proControlsToggleAutoExposeIsoAndDoNotOfferUnsupportedFocus() {
        val controller = CameraManualController(compose.activity, {}).apply {
            attach(CameraManualCapabilities(1_000_000L..500_000_000L, 100..3200,
                whiteBalance = listOf(CameraWhiteBalance.AUTO, CameraWhiteBalance.DAYLIGHT))) {
                Futures.immediateFuture<Void?>(null)
            }
        }
        val exposure = CameraExposureCompensation().apply { attach(-6..6, .5f) }
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraProControls(controller, exposure, true, false, false, {}, {})
        } }
        compose.onNodeWithTag("camera-pro/FOCUS").assertIsNotEnabled()
        compose.onNodeWithTag("camera-pro-controls").captureRoboImage("build/previews/camera-pro-controls.png")
        compose.onNodeWithTag("camera-pro/ISO").performClick()
        compose.onNodeWithTag("camera-manual-iso").assertIsNotEnabled()
        compose.onNodeWithTag("camera-pro-auto").performClick()
        compose.onNodeWithTag("camera-manual-iso").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.runOnIdle { assertEquals(3200, controller.sensorIso) }
        compose.onNodeWithTag("camera-pro/EV").assertIsNotEnabled()
        compose.onNodeWithTag("camera-pro/WB").performClick()
        compose.onNodeWithText("日光").performClick()
        compose.runOnIdle { assertEquals(CameraWhiteBalance.DAYLIGHT, controller.whiteBalance) }
        compose.onNodeWithTag("camera-pro-reset").performClick()
        compose.onNodeWithTag("camera-pro/EV").assertIsEnabled()
    }
}
