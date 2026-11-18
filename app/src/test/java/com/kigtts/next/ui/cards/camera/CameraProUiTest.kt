package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import android.hardware.camera2.params.ColorSpaceTransform
import android.util.Rational
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
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

    @Test fun theWhiteBalanceRulerShowsKelvinAndHdrAutoRestoresAutomaticMode() {
        val matrix = DoubleArray(9) { if (it % 4 == 0) 1.0 else 0.0 }
        val measured = CameraMeteringSample(null, null, null, null,
            colorTransform = ColorSpaceTransform(Array(9) { Rational(matrix[it].toInt(), 1) }))
        val controller = CameraManualController(compose.activity, {}).apply {
            attach(CameraManualCapabilities(temperatureControl =
                CameraTemperatureControl.Calibrated(SensorColorCalibration(6504, matrix), null))) {
                Futures.immediateFuture<Void?>(null)
            }
            record(measured)
        }
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraProAdjustment(ProControl.WB, controller, measured, true, Modifier.size(360.dp, 48.dp))
        } }
        compose.onNodeWithText("2500 K").assertExists()
        compose.onNodeWithText("10000 K").assertExists()
        compose.onNodeWithTag("camera-pro-auto").assertIsNotEnabled()
        compose.onNodeWithTag("camera-manual-white-balance").performSemanticsAction(SemanticsActions.SetProgress) { it(6185f) }
        compose.onNodeWithText("6185 K").assertExists()
        compose.runOnIdle { assertEquals(6185, controller.whiteBalanceKelvin) }
        compose.onNodeWithTag("camera-pro-auto").performClick()
        compose.runOnIdle { assertTrue(controller.automaticWhiteBalance) }
        compose.onNodeWithTag("camera-pro-auto").assertIsNotEnabled()
    }

    @Test fun landscapeParametersStayUprightAndShrinkLongValuesToFitTheRail() {
        val controller = CameraManualController(compose.activity, {}).apply {
            attach(CameraManualCapabilities(1_000_000L..500_000_000L, 100..2_000_000)) { Futures.immediateFuture<Void?>(null) }
            setManualShutter(true, sensitivity = 2_000_000)
        }
        val exposure = CameraExposureCompensation()
        var railWidth by mutableStateOf(80.dp)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CameraProControls(controller, exposure, true, true, {}, Modifier.size(railWidth, 350.dp))
        } }
        val iso = compose.onNodeWithText("2000000", useUnmergedTree = true)
        fun textLayout(): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            iso.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }
        val wide = textLayout()
        compose.runOnIdle { railWidth = CAMERA_PRO_BAND.dp }
        val narrow = textLayout()
        assertTrue(narrow.getBoundingBox(1).width < wide.getBoundingBox(1).width)
        assertFalse(narrow.hasVisualOverflow)
        assertFalse(narrow.isLineEllipsized(0))
        val bounds = iso.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width > bounds.height)
        val caption = compose.onNodeWithText("ISO", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(caption.top >= bounds.bottom)
        val focus = compose.onNodeWithTag("camera-pro/FOCUS").fetchSemanticsNode().boundsInRoot
        val reset = compose.onNodeWithTag("camera-pro-reset").fetchSemanticsNode().boundsInRoot
        assertTrue(focus.bottom < reset.top)
    }

    @Test fun globalExposureSitsLeftOfZoomAndTheRulerDoesNotEraseFocusOffset() {
        val exposure = CameraExposureCompensation().apply { attach(-9..12, 1f / 3f); chooseFocusOffset(2) }
        var expanded by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Box(Modifier.size(360.dp, 240.dp)) {
                val rail = Rect(0f, 192f, 360f, 240f)
                CameraZoomRail(1f, .6f, 8f, true, {}, false, Modifier.matchParentSize(), rail,
                    adjustment = ProControl.EV.takeIf { expanded }) {
                    CameraExposureRuler(exposure, expanded, exposure::chooseBaseline, { expanded = false })
                }
                if (!expanded) CameraExposureRail(exposure, rail, false, 4, true) { expanded = true }
            }
        } }
        val button = compose.onNodeWithTag("camera-global-exposure")
        assertTrue(button.getUnclippedBoundsInRoot().right <=
            compose.onNodeWithTag("camera-zoom-stop/0.6").getUnclippedBoundsInRoot().left)
        val zoomBounds = compose.onNodeWithTag("camera-zoom-rail").getUnclippedBoundsInRoot()
        button.performClick()
        val rulerBounds = compose.onNodeWithTag("camera-exposure-ruler").getUnclippedBoundsInRoot()
        assertEquals((zoomBounds.left.value + zoomBounds.right.value) / 2, (rulerBounds.left.value + rulerBounds.right.value) / 2, .5f)
        assertEquals((zoomBounds.top.value + zoomBounds.bottom.value) / 2, (rulerBounds.top.value + rulerBounds.bottom.value) / 2, .5f)
        compose.onNodeWithTag("camera-zoom-value").assertDoesNotExist()
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
        var selected by mutableStateOf<ProControl?>(null)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Column {
                Box(Modifier.size(360.dp, 72.dp)) {
                    val rail = Rect(0f, 0f, 360f, 48f)
                    CameraZoomRail(1f, .6f, 8f, true, {}, false, Modifier.matchParentSize(), rail, adjustment = selected) { active ->
                        CameraProAdjustment(active, controller, null, true, Modifier)
                    }
                }
                CameraProControls(controller, exposure, true, false, exposure::chooseBaseline, Modifier.size(360.dp, 54.dp),
                    selected = selected, select = { selected = it })
            }
        } }
        compose.onNodeWithTag("camera-pro/FOCUS").assertIsNotEnabled()
        compose.onNodeWithTag("camera-pro-controls").captureRoboImage("build/previews/camera-pro-controls.png")
        compose.onNodeWithTag("camera-pro/ISO").performClick()
        compose.onNodeWithTag("camera-pro-auto").assertIsNotEnabled()
        compose.onNodeWithTag("camera-manual-iso").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        compose.runOnIdle { assertTrue(controller.sensorIso in 564..567) }
        compose.onNodeWithTag("camera-manual-iso").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.runOnIdle { assertEquals(3200, controller.sensorIso) }
        compose.onNodeWithTag("camera-pro/EV").assertIsNotEnabled()
        compose.onNodeWithTag("camera-pro-auto").performClick()
        compose.runOnIdle { assertTrue(controller.automaticExposure) }
        compose.onNodeWithTag("camera-pro/WB").performClick()
        compose.onNodeWithTag("camera-manual-white-balance")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.runOnIdle { assertEquals(CameraWhiteBalance.DAYLIGHT, controller.whiteBalance) }
        compose.onNodeWithTag("camera-pro-reset").performClick()
        compose.onNodeWithTag("camera-pro/EV").assertIsEnabled()
    }
}
