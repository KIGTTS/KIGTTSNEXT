package com.kigtts.engines.streaming

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IncrementalAsrEngineTest {
    private lateinit var root: File; private lateinit var store: RecognitionPackStore
    @Before fun setup() { root = Files.createTempDirectory("online-asr-test").toFile(); store = RecognitionPackStore(PluginStorage(root)); store.import(fixture(root)) }
    @After fun cleanup() { root.deleteRecursively() }
    private fun audio() = PcmAudio(ByteArray(640).also { it[0] = 1 })
    private class Decoder(private val endpointAt: Int = Int.MAX_VALUE) : OnlineSession {
        var count = 0; var pending = false; var closed = false; var finished = false; var value = ""; var resets = 0
        override fun accept(samples: FloatArray) { if (samples.any { it != 0f }) { count++; pending = true } }
        override fun ready() = pending
        override fun decode() { pending = false; value = if (count % 2 == 1) "你" else "你好" }
        override fun text() = value
        override fun endpoint() = count > 0 && count % endpointAt == 0
        override fun reset() { resets++; value = "" }
        override fun finishInput() { finished = true }
        override fun close() { closed = true }
    }
    @Test fun realHypothesisEventsArriveBeforeEofAndTailIsFinalized() = runBlocking {
        val decoder = Decoder(); val engine = IncrementalAsrEngine(store) { _, _, _ -> decoder }
        val events = java.util.concurrent.CopyOnWriteArrayList<RecognitionEvent>(); val partialSeen = CompletableDeferred<Unit>()
        engine.recognizeStream(flow {
            emit(audio()); withTimeout(3_000) { partialSeen.await() }; assertFalse(decoder.finished)
            emit(audio())
        }).collect { events += it; if (it == RecognitionEvent.Partial("你")) partialSeen.complete(Unit) }
        assertTrue(events.any { it == RecognitionEvent.Partial("你好") })
        assertEquals(RecognitionEvent.Final(RecognitionResult("你好")), events.last()); assertTrue(decoder.closed); assertTrue(decoder.finished)
    }
    @Test fun endpointsFinalizeAndResetWithoutDuplicateTrailingFinal() = runBlocking {
        val decoder = Decoder(2); val engine = IncrementalAsrEngine(store) { _, _, _ -> decoder }
        val events = engine.recognizeStream(flow { repeat(4) { emit(audio()) } }).toList()
        assertEquals(2, events.filterIsInstance<RecognitionEvent.Final>().size); assertEquals(2, decoder.resets)
        assertEquals(2, events.filterIsInstance<RecognitionEvent.Partial>().size); assertTrue(decoder.closed)
    }
    @Test fun cancellationReleasesNativeObjectsAndAllowsAnotherSession() = runBlocking {
        val opened = mutableListOf<Decoder>(); val engine = IncrementalAsrEngine(store) { _, _, _ -> Decoder().also { opened += it } }
        withTimeout(3_000) { engine.recognizeStream(flow { emit(audio()); awaitCancellation() }).first { it is RecognitionEvent.Partial } }
        assertTrue(opened.single().closed); assertFalse(store.session.isLocked)
        assertEquals("你", engine.recognize(audio()).text); assertTrue(opened.last().closed)
    }
    @Test fun silenceEmptyInputAndFailuresDoNotFabricateTextOrRetainSession() = runBlocking {
        val opened = mutableListOf<Decoder>(); val engine = IncrementalAsrEngine(store) { _, _, _ -> Decoder().also { opened += it } }
        assertEquals(listOf(RecognitionEvent.Started), engine.recognizeStream(flowOf(PcmAudio(ByteArray(640)))).toList())
        assertEquals("", engine.recognize(PcmAudio(byteArrayOf())).text)
        try { engine.recognizeStream(flowOf(PcmAudio(byteArrayOf(0, 0), 8_000))).toList(); fail("Bad format accepted") }
        catch (expected: EngineException) { assertEquals(EngineError.UNSUPPORTED_FORMAT, expected.code) }
        assertTrue(opened.all { it.closed }); assertFalse(store.session.isLocked)
    }
    @Test fun fullRecordingUsesSameOnlineBackendWithoutSentenceEndpointing() = runBlocking {
        var endpoints: Boolean? = null; val decoder = Decoder(1)
        val engine = IncrementalAsrEngine(store) { _, _, enabled -> endpoints = enabled; decoder }
        assertEquals("你", engine.recognize(audio()).text); assertEquals(false, endpoints); assertTrue(decoder.finished); assertTrue(decoder.closed)
        assertEquals(StreamingKind.INCREMENTAL, engine.info.streaming)
    }
}
