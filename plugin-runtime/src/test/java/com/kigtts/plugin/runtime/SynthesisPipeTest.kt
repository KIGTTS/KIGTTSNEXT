package com.kigtts.plugin.runtime

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Test

class SynthesisPipeTest {
    @Test fun coldFlowCreatesAnIndependentSessionForEveryCollection() = runBlocking<Unit> {
        val tokens = mutableListOf<String>()
        val audio = synthesisPipe({ token -> tokens += token; error("No worker in unit test") }, { error("No pipe to cancel") })
        assertTrue(tokens.isEmpty())
        repeat(2) { assertTrue(runCatching { audio.toList() }.isFailure) }
        assertEquals(2, tokens.distinct().size)
    }
}
