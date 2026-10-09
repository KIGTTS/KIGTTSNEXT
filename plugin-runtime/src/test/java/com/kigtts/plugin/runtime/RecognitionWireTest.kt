package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.*

class RecognitionWireTest {
    private fun recording(finish: Boolean): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(AudioWire.START); AudioWire.text(output, AudioWire.PARTIAL, "实时字幕")
            AudioWire.text(output, AudioWire.FINAL, PluginJson.encodeToString(RecognitionResult("最后一句")))
            if (finish) output.writeInt(AudioWire.FINISH)
        }; return bytes.toByteArray()
    }
    @Test fun streamedResponsesPreserveRealEventsAndAcceptExplicitCompletion() = runBlocking {
        val events = mutableListOf<RecognitionEvent>()
        readRecognitionResponses(DataInputStream(ByteArrayInputStream(recording(true)))) { events += it }
        assertEquals(listOf(RecognitionEvent.Started, RecognitionEvent.Partial("实时字幕"), RecognitionEvent.Final(RecognitionResult("最后一句"))), events)
    }
    @Test fun workerDeathAfterFinalIsStillAnInterruptedSession() = runBlocking<Unit> {
        val events = mutableListOf<RecognitionEvent>()
        try { readRecognitionResponses(DataInputStream(ByteArrayInputStream(recording(false)))) { events += it }; fail("Unexpected EOF accepted") }
        catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("中断")) }
        assertTrue(events.last() is RecognitionEvent.Final)
    }
}
