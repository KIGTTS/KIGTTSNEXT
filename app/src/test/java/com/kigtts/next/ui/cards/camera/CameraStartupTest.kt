@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraStartupTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun exactModernSessionIsReleasedOnceWithoutTouchingAnotherSession() {
        val owned = modernSession(); val unrelated = modernSession()
        val bound = java.util.IdentityHashMap<SessionConfig, Boolean>().apply { put(owned, true); put(unrelated, true) }
        var calls = 0
        val lease = CameraSessionLease(owned) { candidate ->
            assertSame(owned, candidate); assertFalse(candidate.isLegacy)
            bound.remove(candidate); calls++
        }
        lease.close(); lease.close()
        assertEquals(1, calls); assertFalse(bound.containsKey(owned)); assertTrue(bound.containsKey(unrelated))
    }

    @Test fun onlyOpenAndStreamingTogetherTransferTheSession() = runTest(dispatcher) {
        val state = MutableLiveData(CameraState.create(CameraState.Type.OPENING))
        val stream = MutableLiveData(PreviewView.StreamState.IDLE)
        var releases = 0; val lease = CameraSessionLease(modernSession()) { releases++ }
        val startup = async { finishCameraStartup(lease, { it.close() }) { awaitCameraReady(state, stream, 1_000) } }
        runCurrent(); state.value = CameraState.create(CameraState.Type.OPEN)
        runCurrent(); assertFalse(startup.isCompleted)
        state.value = CameraState.create(CameraState.Type.OPENING); stream.value = PreviewView.StreamState.STREAMING
        runCurrent(); assertFalse(startup.isCompleted)
        state.value = CameraState.create(CameraState.Type.OPEN)
        runCurrent(); assertSame(lease, startup.await()); assertEquals(0, releases)
        assertFalse(state.hasObservers()); assertFalse(stream.hasObservers())
        lease.close(); assertEquals(1, releases)
    }

    @Test fun errorlessOpeningHasFiniteTimeoutAndReleasesItsSession() = runTest(dispatcher) {
        supervisorScope {
            val state = MutableLiveData(CameraState.create(CameraState.Type.OPENING))
            val stream = MutableLiveData(PreviewView.StreamState.IDLE)
            var releases = 0; val lease = CameraSessionLease(modernSession()) { releases++ }
            val startup = async { finishCameraStartup(lease, { it.close() }) { awaitCameraReady(state, stream, 400) } }
            runCurrent(); advanceTimeBy(399); runCurrent(); assertFalse(startup.isCompleted)
            advanceTimeBy(1); runCurrent()
            val failure = try { startup.await(); null } catch (problem: Exception) { problem }
            assertTrue(failure is CameraStartupException); assertTrue(failure!!.message!!.contains("OPENING"))
            assertEquals(1, releases); assertFalse(state.hasObservers()); assertFalse(stream.hasObservers())
        }
    }

    @Test fun runtimeCameraFailureReleasesBeforeTheFallbackCanStart() = runTest(dispatcher) {
        supervisorScope {
            val state = MutableLiveData(CameraState.create(CameraState.Type.OPENING))
            val stream = MutableLiveData(PreviewView.StreamState.IDLE)
            val events = mutableListOf<String>(); val lease = CameraSessionLease(modernSession()) { events += "released" }
            val startup = async { finishCameraStartup(lease, { it.close() }) { awaitCameraReady(state, stream, 1_000) } }
            runCurrent()
            state.value = CameraState.create(CameraState.Type.OPENING, CameraState.StateError.create(CameraState.ERROR_STREAM_CONFIG))
            runCurrent()
            val failure = try { startup.await(); null } catch (problem: Exception) { problem }
            assertTrue(failure is CameraStartupException); assertEquals(listOf("released"), events)
            assertTrue(testScheduler.currentTime < 1_000)
            assertFalse(state.hasObservers()); assertFalse(stream.hasObservers())
        }
    }

    @Test fun leavingDuringStartupReleasesAllocatedSessionAndObservers() = runTest(dispatcher) {
        val state = MutableLiveData(CameraState.create(CameraState.Type.OPENING))
        val stream = MutableLiveData(PreviewView.StreamState.IDLE)
        var releases = 0; val lease = CameraSessionLease(modernSession()) { releases++ }
        val startup = launch { finishCameraStartup(lease, { it.close() }) { awaitCameraReady(state, stream) } }
        runCurrent(); assertTrue(state.hasObservers()); assertTrue(stream.hasObservers())
        startup.cancel(); runCurrent(); startup.join()
        assertEquals(1, releases); assertFalse(state.hasObservers()); assertFalse(stream.hasObservers())
    }

    @Test fun previousStreamingValueMustBecomeIdleBeforeAnotherSessionStarts() = runTest(dispatcher) {
        val stream = MutableLiveData(PreviewView.StreamState.STREAMING)
        val readyForNewSession = async { awaitCameraPreviewIdle(stream) }
        runCurrent(); assertFalse(readyForNewSession.isCompleted)
        stream.value = PreviewView.StreamState.IDLE
        runCurrent(); readyForNewSession.await(); assertFalse(stream.hasObservers())
    }

    @Test fun cameraCloseBarrierWaitsUntilTheOldCameraStops() = runTest(dispatcher) {
        val state = MutableLiveData(CameraState.create(CameraState.Type.OPEN))
        val closed = async { awaitCameraClosed(state) }
        runCurrent(); assertFalse(closed.isCompleted)
        state.value = CameraState.create(CameraState.Type.CLOSING)
        runCurrent(); assertFalse(closed.isCompleted)
        state.value = CameraState.create(CameraState.Type.CLOSED)
        runCurrent(); closed.await(); assertFalse(state.hasObservers())
    }

    private fun modernSession() = SessionConfig(listOf(Preview.Builder().build(), ImageCapture.Builder().build()))
}
