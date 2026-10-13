package com.kigtts.next.ui.cards.camera

import androidx.activity.ComponentActivity
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.util.concurrent.Futures
import com.kigtts.next.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class CameraManualPanelUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun manualChoiceAndNativeSliderSubmitSupportedRequestsAndReturningToAutoClearsOverrides() {
        val requests = mutableListOf<CaptureRequestOptions?>()
        val controller = CameraManualController(compose.activity, {}, executor = Executor { it.run() }).apply {
            attach(CameraManualCapabilities(1_000_000L..500_000_000L, 100..1600, 8f)) {
                requests += it; Futures.immediateFuture<Void?>(null)
            }
            record(CameraMeteringSample(10_000_000L, 400, 33_333_333L, 2f))
        }
        compose.setContent { KigttsTheme(ThemeSettings()) {
            Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) { CameraManualPanel(controller, true) }
        } }
        compose.onNodeWithContentDescription("对焦：手动").performScrollTo().performClick()
        compose.onNodeWithTag("camera-manual-focus").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        assertTrue(controller.manualFocus); assertEquals(6f, controller.focusDiopters, .001f)
        assertEquals(6f, requireNotNull(requests.last()).getCaptureRequestOption(android.hardware.camera2.CaptureRequest.LENS_FOCUS_DISTANCE)!!, .001f)
        compose.onNodeWithContentDescription("快门：手动").performScrollTo().performClick()
        compose.onNodeWithTag("camera-manual-shutter").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        assertEquals(500_000_000L, controller.shutterNanos)
        assertEquals(400, controller.sensorIso)
        compose.onNodeWithContentDescription("快门：自动").performScrollTo().performClick()
        compose.onNodeWithContentDescription("对焦：自动").performScrollTo().performClick()
        assertTrue(controller.automaticExposure); assertFalse(controller.manualFocus); assertNull(requests.last())
    }

    @Test fun unavailableManualHardwareNeverOffersAnActiveManualChoice() {
        val controller = CameraManualController(compose.activity, {}).apply {
            attach(CameraManualCapabilities(reason = "手动调整仅标准模式支持"), null)
        }
        compose.setContent { KigttsTheme(ThemeSettings()) { CameraManualPanel(controller, true) } }
        compose.onNodeWithContentDescription("对焦：手动").assertIsNotEnabled()
        compose.onNodeWithContentDescription("快门：手动").assertIsNotEnabled()
        compose.onNodeWithTag("camera-manual-shutter").assertDoesNotExist()
        compose.onNodeWithTag("camera-manual-focus").assertDoesNotExist()
    }
}
