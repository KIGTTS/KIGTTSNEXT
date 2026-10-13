@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next.ui.cards.camera

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class CameraCountdownTest {
    @Test fun threeSecondCountdownShowsEachSecondAndHandsOffExactlyOnce() = runTest {
        val countdown = CameraCountdownState(this); var photos = 0; var restored = 0
        assertTrue(countdown.start(3, { photos++ }, { restored++ }))
        assertTrue(countdown.active); assertEquals(3, countdown.remainingSeconds)
        runCurrent(); advanceTimeBy(1_000); runCurrent()
        assertEquals(2, countdown.remainingSeconds); assertEquals(0, photos)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, countdown.remainingSeconds); assertEquals(0, photos)
        advanceTimeBy(1_000); runCurrent()
        assertFalse(countdown.active); assertEquals(0, countdown.remainingSeconds)
        assertEquals(1, photos); assertEquals(0, restored)
        advanceTimeBy(60_000); runCurrent(); assertEquals(1, photos)
    }

    @Test fun repeatedShutterTapsCannotCreateAnotherPendingPhotograph() = runTest {
        val countdown = CameraCountdownState(this); var photos = 0
        assertTrue(countdown.start(10, { photos++ }, {})); runCurrent()
        repeat(20) { assertFalse(countdown.start(if (it % 2 == 0) 0 else 3, { photos++ }, {})) }
        advanceTimeBy(9_999); runCurrent(); assertEquals(0, photos)
        advanceTimeBy(1); runCurrent(); assertEquals(1, photos); assertFalse(countdown.active)
    }

    @Test fun cancelRestoresControlsOnceAndNeverFiresItsOldTimer() = runTest {
        val countdown = CameraCountdownState(this); var photos = 0; var restored = 0
        countdown.start(10, { photos++ }, { restored++ }); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertTrue(countdown.cancel()); assertFalse(countdown.cancel())
        assertFalse(countdown.active); assertEquals(0, countdown.remainingSeconds); assertEquals(1, restored)
        advanceTimeBy(20_000); runCurrent(); assertEquals(0, photos); assertEquals(1, restored)
    }

    @Test fun cancelAtTheFinalTickStillPreventsExposureAndAllowsANewCountdown() = runTest {
        val countdown = CameraCountdownState(this); var photos = 0; var restored = 0
        countdown.start(3, { photos++ }, { restored++ }); runCurrent()
        // advanceTimeBy leaves the task due exactly at 3000 ms queued; returning now must cancel it.
        advanceTimeBy(3_000)
        assertTrue(countdown.cancel()); runCurrent(); assertEquals(0, photos)
        assertTrue(countdown.start(3, { photos++ }, { restored++ })); runCurrent()
        advanceTimeBy(3_000); runCurrent()
        assertEquals(1, photos); assertEquals(1, restored); assertFalse(countdown.active)
    }

    @Test fun leavingTheOwningScopeCancelsDelayAndRestoresWithoutTakingAPhoto() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val countdown = CameraCountdownState(owner); var photos = 0; var restored = 0
        countdown.start(10, { photos++ }, { restored++ }); runCurrent()
        owner.cancel(); runCurrent()
        assertFalse(countdown.active); assertEquals(0, countdown.remainingSeconds); assertEquals(1, restored)
        advanceTimeBy(10_000); runCurrent(); assertEquals(0, photos)
    }

    @Test fun zeroDelayStartsSynchronouslyAndCannotBeMistakenForSavingOrACancellableDelay() = runTest {
        val countdown = CameraCountdownState(this); var photos = 0; var restored = 0
        val startedAt = testScheduler.currentTime
        assertTrue(countdown.start(0, { photos++ }, { restored++ }))
        assertEquals(1, photos); assertEquals(startedAt, testScheduler.currentTime)
        assertFalse(countdown.active); assertEquals(0, countdown.remainingSeconds)
        assertFalse(countdown.cancel()); assertEquals(0, restored)
    }

    @Test fun unsupportedDelayAndDisposedOwnerAreRejectedWithoutLeavingCountingState() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val countdown = CameraCountdownState(owner); var photos = 0
        assertFalse(countdown.start(2, { photos++ }, {})); assertFalse(countdown.start(-1, { photos++ }, {}))
        owner.cancel()
        assertFalse(countdown.start(3, { photos++ }, {}))
        assertFalse(countdown.active); assertEquals(0, countdown.remainingSeconds); assertEquals(0, photos)
    }
}
