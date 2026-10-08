package com.kigtts.plugins.tts

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceholderTtsTest {
    @Test fun placeholderNeverEmitsAudio() = runTest {
        val events = PlaceholderTtsPlugin().tts.synthesize(TtsRequest("你好")).toList()
        assertEquals(SynthesisEvent.Started, events.first())
        assertTrue(events.last() is SynthesisEvent.Finished)
        assertTrue(events.none { it is SynthesisEvent.Audio })
    }
    @Test fun cancellationStopsCompletionEvent() = runTest {
        val events = mutableListOf<SynthesisEvent>()
        val job = launch { PlaceholderTtsPlugin().tts.synthesize(TtsRequest("你好")).collect { events.add(it) } }
        runCurrent(); job.cancelAndJoin(); advanceUntilIdle()
        assertEquals(listOf<SynthesisEvent>(SynthesisEvent.Started), events)
    }
}
