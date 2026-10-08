package com.kigtts.plugins.asr

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceholderAsrTest {
    private val plugin = PlaceholderAsrPlugin()
    @Test fun nonStreamingReturnsOneExplicitPlaceholder() = runTest {
        val result = plugin.asr.recognize(PcmAudio(ByteArray(32)))
        assertTrue(result.placeholder)
        assertTrue(result.text.isNotBlank())
    }
    @Test fun streamingPublishesPartialsThenOneFinal() = runTest {
        val events = plugin.asr.recognizeStream(flow { repeat(4) { emit(PcmAudio(ByteArray(32))) } }).toList()
        assertEquals(RecognitionEvent.Started, events.first())
        assertEquals(4, events.filterIsInstance<RecognitionEvent.Partial>().size)
        val final = events.filterIsInstance<RecognitionEvent.Final>().single()
        assertTrue(final.result.placeholder)
        assertEquals(events.filterIsInstance<RecognitionEvent.Partial>().last().text, final.result.text)
    }
    @Test fun cancellationClosesInputAndDoesNotPublishFinal() = runTest {
        var released = false
        val events = mutableListOf<RecognitionEvent>()
        val input = flow { try { emit(PcmAudio(ByteArray(32))); awaitCancellation() } finally { released = true } }
        val job = launch { plugin.asr.recognizeStream(input).collect { events.add(it) } }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(released)
        assertTrue(events.none { it is RecognitionEvent.Final })
    }
    @Test fun emptyStreamHasEmptyFinalRatherThanInventedTranscript() = runTest {
        val events = plugin.asr.recognizeStream(emptyFlow()).toList()
        assertEquals("", (events.last() as RecognitionEvent.Final).result.text)
    }
}
