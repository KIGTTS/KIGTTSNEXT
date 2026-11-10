@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next.ui.cards.camera

import android.content.Context
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraManualControllerTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun reset() { Dispatchers.resetMain() }
    private fun capabilities() = CameraManualCapabilities(1_000_000L..2_000_000_000L, 100..3200, 10f, 2_000_000_000L, true)
    private fun controller(message: (String) -> Unit = {}) = CameraManualController(
        ApplicationProvider.getApplicationContext<Context>(), message, executor = Executor { it.run() })

    @Test fun manualExposureUsesActualRecentIsoAndFrameDurationThenRestoresAllAutoOverrides() {
        val requests = mutableListOf<CaptureRequestOptions?>()
        val controller = controller()
        controller.attach(capabilities()) { request -> requests += request; Futures.immediateFuture<Void?>(null) }
        controller.record(CameraMeteringSample(20_000_000L, 800, 33_333_333L, 2.5f))
        controller.setManualShutter(true); controller.exposeFor(250_000_000L)
        val exposed = requireNotNull(requests.last())
        assertEquals(CaptureRequest.CONTROL_AE_MODE_OFF, requireNotNull(exposed.getCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE)))
        assertEquals(250_000_000L, requireNotNull(exposed.getCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME)))
        assertEquals(800, requireNotNull(exposed.getCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY)))
        assertEquals(250_000_000L, requireNotNull(exposed.getCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION)))
        controller.focusMode(true)
        assertEquals(2.5f, controller.focusDiopters, .001f)
        controller.focusAt(30f)
        val focused = requireNotNull(requests.last())
        assertEquals(CaptureRequest.CONTROL_AF_MODE_OFF, requireNotNull(focused.getCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE)))
        assertEquals(10f, requireNotNull(focused.getCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE)), .001f)
        controller.setManualShutter(false); controller.focusMode(false)
        assertNull(requests.last()); assertTrue(controller.automaticExposure); assertFalse(controller.manualFocus)
    }

    @Test fun aStaleRejectedRequestNeverRollsBackANewerSelectionOrTheNewCameraOwner() {
        val requests = mutableListOf<CaptureRequestOptions?>(); val futures = mutableListOf<SettableFuture<Void?>>()
        val messages = mutableListOf<String>(); val controller = controller(messages::add)
        fun attach() = controller.attach(capabilities()) { request ->
            requests += request; SettableFuture.create<Void?>().also { futures += it }
        }
        attach(); controller.focusMode(true); controller.focusAt(3f)
        futures[0].setException(IllegalArgumentException("Old setting"))
        assertTrue(controller.manualFocus); assertEquals(3f, controller.focusDiopters, .001f); assertTrue(messages.isEmpty())
        val old = futures[1]
        controller.unbind(); attach(); controller.setManualShutter(true)
        old.setException(IllegalArgumentException("Old camera"))
        assertFalse(controller.automaticExposure); assertTrue(messages.isEmpty())
        futures.last().setException(IllegalArgumentException("New request"))
        assertTrue(controller.automaticExposure); assertNull(requests.last()); assertEquals(1, messages.size)
    }

    @Test fun unsupportedHardwareNeverSendsManualRequestsAndTimeSelectionStaysInTheReportedRange() {
        val controller = controller(); var requests = 0
        controller.attach(CameraManualCapabilities(reason = "手动调整仅标准模式支持")) { requests++; Futures.immediateFuture<Void?>(null) }
        controller.focusMode(true); controller.setManualShutter(true)
        assertEquals(0, requests); assertFalse(controller.manualFocus); assertTrue(controller.automaticExposure)
        val range = 2_000_000L..500_000_000L
        assertTrue(shutterStops(range).all { it in range }); assertFalse(1_000_000_000L in shutterStops(range))
        assertEquals(range.first, shutterAt(0f, range)); assertEquals(range.last, shutterAt(1f, range))
        assertEquals(.5f, shutterFraction(shutterAt(.5f, range), range), .0001f)
    }

    @Test fun proIsoAndWhiteBalanceUseOneRequestWithoutDroppingTheManualShutter() {
        val requests = mutableListOf<CaptureRequestOptions?>()
        val controller = controller()
        controller.attach(capabilities().copy(whiteBalance = listOf(CameraWhiteBalance.AUTO, CameraWhiteBalance.DAYLIGHT))) {
            requests += it; Futures.immediateFuture<Void?>(null)
        }
        controller.record(CameraMeteringSample(20_000_000L, 800, null, null))
        controller.setManualShutter(true); controller.sensitivityAt(2000)
        controller.selectWhiteBalance(CameraWhiteBalance.DAYLIGHT)
        val options = requireNotNull(requests.last())
        assertEquals(2000, requireNotNull(options.getCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY)))
        assertEquals(20_000_000L, requireNotNull(options.getCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME)))
        assertEquals(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT, requireNotNull(options.getCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE)))
        controller.selectWhiteBalance(CameraWhiteBalance.SHADE)
        assertSame(options, requests.last())
        controller.reset()
        assertNull(requests.last())
        assertTrue(controller.automaticExposure)
        assertEquals(CameraWhiteBalance.AUTO, controller.whiteBalance)
    }

    @Test fun captureWaitsForTheRealRequestAcknowledgementBeforeItMayProceed() = runTest(dispatcher) {
        val applied = SettableFuture.create<Void?>(); val controller = controller()
        controller.attach(capabilities()) { applied }
        controller.setManualShutter(true)
        val captureGate = async { controller.awaitConfigured() }
        runCurrent(); assertFalse(captureGate.isCompleted)
        applied.set(null); ShadowLooper.idleMainLooper(); runCurrent()
        captureGate.await(); assertTrue(captureGate.isCompleted)
    }
}
