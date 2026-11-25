package com.kigtts.next.ui.cards.camera

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Real recording events exercise the pre-roll lifecycle without requiring a hardware encoder. */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraMotionBufferTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Source(private val now: () -> Long, private val finishOnStop: Boolean = true) : CameraMotionSource {
        class Recording(val file: File, val started: Long, val events: (CameraMotionEvent) -> Unit) {
            var stops = 0
        }
        val recordings = mutableListOf<Recording>()
        override fun start(file: File, events: (CameraMotionEvent) -> Unit): CameraMotionRecording {
            file.writeBytes(byteArrayOf(1, 2, 3))
            val recording = Recording(file, now(), events).also { recordings += it }
            events(CameraMotionEvent.Started(0))
            return CameraMotionRecording {
                recording.stops++
                if (finishOnStop) finish(recording, now() - recording.started)
            }
        }
        fun progress(duration: Long = 250) { recordings.last().events(CameraMotionEvent.Progress(duration)) }
        fun finish(recording: Recording = recordings.last(), duration: Long, usable: Boolean = true) {
            recording.events(CameraMotionEvent.Finished(duration, usable))
        }
    }
    private fun matrix() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    @Test fun shutterIncludesActualPreRollAcrossSegmentsAndUsesTheExposureCallbackClock() = runTest {
        val source = Source({ testScheduler.currentTime })
        var muxed = emptyList<CameraMotionPart>(); var shutter = -1L
        val buffer = CameraMotionBuffer(temporary.root, source, backgroundScope, { fail(it) },
            { testScheduler.currentTime }) { parts, exposure, output ->
            muxed = parts; shutter = exposure; output.writeBytes(byteArrayOf(8, 9))
            CameraMotionVideo(3200, 90, 2000)
        }
        try {
            buffer.start(); source.progress()
            advanceTimeBy(6000); source.finish(duration = 6000)
            advanceTimeBy(400)
            val capture = requireNotNull(buffer.beginCapture())
            advanceTimeBy(150); buffer.exposureStarted(capture)
            advanceTimeBy(450); source.finish(duration = 1000)
            val inputMatrix = matrix()
            val delivered = async { buffer.finish(capture, inputMatrix) }
            advanceTimeBy(749); runCurrent()
            assertFalse(delivered.isCompleted)
            assertEquals(0, source.recordings.last().stops)
            advanceTimeBy(1); runCurrent()
            val clip = requireNotNull(delivered.await())
            assertEquals(6550L, shutter)
            assertEquals(listOf(0L, 6000L, 7000L), muxed.map { it.startedAtMs })
            assertTrue(muxed.first().startedAtMs + muxed.first().durationMs < shutter)
            assertTrue(muxed.last().startedAtMs + muxed.last().durationMs > shutter)
            assertEquals(1, source.recordings.last().stops)
            assertEquals(90, clip.rotationDegrees); assertEquals(2000, clip.shutterPositionMs)
            assertTrue(clip.video.isFile)
            assertTrue(source.recordings.all { !it.file.exists() })
            inputMatrix[0] = 0f; assertEquals(1f, clip.colorMatrix[0], 0f)
            clip.video.delete()
        } finally { buffer.close() }
    }

    @Test fun waitsForColdBufferAndDoesNotStopBeforeCameraExposure() = runTest {
        val source = Source({ testScheduler.currentTime })
        val buffer = CameraMotionBuffer(temporary.root, source, backgroundScope, { fail(it) }, { testScheduler.currentTime })
        try {
            buffer.start()
            val warmup = async { buffer.awaitReady() }
            runCurrent()
            assertFalse(warmup.isCompleted)
            advanceTimeBy(250)
            source.progress()
            runCurrent()
            warmup.await()
            val capture = requireNotNull(buffer.beginCapture())
            advanceTimeBy(1700)
            runCurrent()
            assertEquals(0, source.recordings.last().stops)
            buffer.exposureStarted(capture)
            advanceTimeBy(1200)
            runCurrent()
            assertEquals(1, source.recordings.last().stops)
        } finally { buffer.close() }
    }

    @Test fun retryableEncoderRolloverRestartsWithoutTakingTheCameraOffline() = runTest {
        val source = Source({ testScheduler.currentTime })
        val buffer = CameraMotionBuffer(temporary.root, source, backgroundScope, { fail(it) }, { testScheduler.currentTime })
        try {
            buffer.start()
            source.recordings.last().events(CameraMotionEvent.Finished(0, false, retryable = true))
            advanceTimeBy(180)
            runCurrent()
            assertEquals(2, source.recordings.size)
            source.progress()
            assertTrue(buffer.ready)
            assertFalse(buffer.failed)
            assertNotNull(buffer.beginCapture())
        } finally { buffer.close() }
    }

    @Test fun ordinaryPreRecordingIsBoundedAndNeverRetainsMoreThanTwoFinishedSegments() = runTest {
        val source = Source({ testScheduler.currentTime })
        val buffer = CameraMotionBuffer(temporary.root, source, backgroundScope, { fail(it) }, { testScheduler.currentTime })
        try {
            buffer.start(); source.progress()
            repeat(3) { advanceTimeBy(6000); source.finish(duration = 6000) }
            assertEquals(4, source.recordings.size)
            assertFalse(source.recordings[0].file.exists())
            assertTrue(source.recordings[1].file.isFile && source.recordings[2].file.isFile)
            assertTrue(source.recordings[3].file.isFile)
        } finally { buffer.close() }
        assertTrue(source.recordings.all { !it.file.exists() })
    }

    @Test fun slowOrMissingFinalizeTimesOutToAStillPhotoInsteadOfCancellingTheCapture() = runTest {
        val source = Source({ testScheduler.currentTime }, finishOnStop = false)
        val messages = mutableListOf<String>()
        val buffer = CameraMotionBuffer(temporary.root, source, backgroundScope, messages::add,
            { testScheduler.currentTime }) { _, _, _ -> error("An unfinished recording cannot be muxed") }
        try {
            buffer.start(); source.progress(); val capture = requireNotNull(buffer.beginCapture())
            val delivered = async(start = CoroutineStart.UNDISPATCHED) { buffer.finish(capture, matrix()) }
            advanceTimeBy(6001); runCurrent()
            assertNull(delivered.await())
            assertTrue(currentCoroutineContext().isActive)
            assertEquals(1, source.recordings.last().stops)
            assertTrue(messages.single().contains("保留照片"))
            assertFalse(temporary.root.listFiles().orEmpty().any { it.name.endsWith("-clip.mp4") })
        } finally { buffer.close() }
    }

    @Test fun pageCancellationPropagatesAndLateEncoderFinalizeCannotLeakAFile() = runTest {
        val source = Source({ testScheduler.currentTime }, finishOnStop = false)
        var muxCalls = 0
        val buffer = CameraMotionBuffer(temporary.root, source, backgroundScope, { fail(it) },
            { testScheduler.currentTime }) { _, _, _ -> muxCalls++; error("Cancelled recording") }
        buffer.start(); source.progress(); val capture = requireNotNull(buffer.beginCapture())
        val delivered = async(start = CoroutineStart.UNDISPATCHED) { buffer.finish(capture, matrix()) }
        buffer.close()
        assertTrue(runCatching { delivered.await() }.exceptionOrNull() is CancellationException)
        val late = source.recordings.last()
        late.file.writeBytes(byteArrayOf(7)) // a device may finish writing after stop returns
        source.finish(late, 1000)
        assertFalse(late.file.exists()); assertEquals(0, muxCalls)
        assertFalse(buffer.ready)
    }

    @Test fun encoderFailureWithdrawsMotionButDoesNotFailTheOrdinaryCameraSession() = runTest {
        val source = Source({ testScheduler.currentTime }, finishOnStop = false)
        val messages = mutableListOf<String>()
        val buffer = CameraMotionBuffer(temporary.root, source, backgroundScope, messages::add, { testScheduler.currentTime })
        try {
            buffer.start(); source.progress(); val capture = requireNotNull(buffer.beginCapture())
            source.finish(duration = 1000, usable = false)
            assertNull(buffer.finish(capture, matrix()))
            assertTrue(buffer.failed); assertFalse(buffer.ready)
            assertNull(buffer.beginCapture())
            assertTrue(messages.any { it.contains("仍可正常拍照") })
            assertTrue(source.recordings.all { !it.file.exists() })
        } finally { buffer.close() }
    }

    @Test fun abortRestartsTheRingAndTheOldTicketCannotReceiveTheNextCapture() = runTest {
        val source = Source({ testScheduler.currentTime })
        val buffer = CameraMotionBuffer(temporary.root, source, backgroundScope, { fail(it) }, { testScheduler.currentTime })
        try {
            buffer.start(); source.progress(); advanceTimeBy(500)
            val first = requireNotNull(buffer.beginCapture()); buffer.abort(first)
            assertTrue(first.finished.isCancelled)
            assertEquals(2, source.recordings.size)
            source.progress(); assertNotNull(buffer.beginCapture())
        } finally { buffer.close() }
    }
}
