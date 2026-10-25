@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next.ui.cards.camera

import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CameraBurstCaptureTest {
    private fun shot() = PolaroidCameraCapture(Files.createTempFile("burst-", ".jpg").toFile(), null,
        PolaroidFormat.MINI, PhotoFilter.ORIGINAL)

    @Test fun releaseFinishesTheActiveExposureThenProcessesInOrderWithoutAnotherCapture() = runTest {
        val burst = CameraBurstCapture(this); var exposures = 0; var finished = 0
        var result = emptyList<PolaroidCameraCapture>(); val order = mutableListOf<String>()
        assertTrue(burst.start({ exposures++; delay(100); shot() }, { order += it.original.path; delay(20); it },
            { true }, { fail(it) }, { result = it }, { finished++ }))
        assertFalse(burst.start({ shot() }, { it }, { true }, {}, {}, {}))
        runCurrent(); advanceTimeBy(200); runCurrent()
        assertEquals(2, exposures); assertEquals(1, burst.count)
        burst.stop(); advanceUntilIdle()
        assertEquals(2, exposures); assertEquals(2, result.size); assertEquals(result.map { it.original.path }, order)
        assertFalse(burst.running); assertFalse(burst.held); assertEquals(1, finished)
        assertTrue(result.all { it.original.isFile }); result.forEach { it.discard() }
    }

    @Test fun losingTheCameraOwnerPreservesCompletedPhotosForReviewAndDoesNotTakeAnotherPhoto() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val burst = CameraBurstCapture(owner); var exposures = 0; var result = emptyList<PolaroidCameraCapture>()
        burst.start({ exposures++; delay(100); shot() }, { it }, { true }, {}, { result = it }, {})
        runCurrent(); advanceTimeBy(180); runCurrent(); owner.cancel(); runCurrent()
        assertEquals(2, exposures); assertEquals(1, result.size); assertTrue(result.single().original.isFile)
        assertFalse(burst.running); result.forEach { it.discard() }
    }

    @Test fun fullStorageStopsBeforeExposureAndADeviceFailureStillOffersCompletedPhotos() = runTest {
        val burst = CameraBurstCapture(this); val messages = mutableListOf<String>(); var shots = 0
        burst.start({ shots++; shot() }, { it }, { false }, { messages += it }, { fail("No photos expected") }, {})
        advanceUntilIdle(); assertEquals(0, shots); assertFalse(burst.running); assertEquals(1, messages.size)
        var result = emptyList<PolaroidCameraCapture>()
        burst.start({ if (++shots == 2) error("camera unavailable") else shot() }, { it }, { true },
            { messages += it }, { result = it }, {})
        advanceUntilIdle(); assertEquals(1, result.size); assertEquals(2, messages.size)
        result.forEach { it.discard() }
    }
}
