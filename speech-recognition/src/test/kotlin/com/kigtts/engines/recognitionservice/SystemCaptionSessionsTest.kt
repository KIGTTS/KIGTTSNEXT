package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.RecognitionConfig
import com.kigtts.plugin.api.RecognitionEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemCaptionSessionsTest {
    @Test fun usesFinalUtterancesAndFlushesTheActiveOneOnSubmit() = runTest {
        val stop = CompletableDeferred<Unit>()
        var requests = 0
        val received = systemCaptionSessions(stop, RecognitionConfig()) {
            when (++requests) {
                1 -> "第一句"
                2 -> "" // The system reported silence, so listen for the next utterance.
                3 -> { stop.complete(Unit); "最后一句" }
                else -> error("Started another recording after submit")
            }
        }.toList()
        assertEquals(listOf(RecognitionEvent.Started,
            RecognitionEvent.Transcript("第一句", "", 1, "第一句"),
            RecognitionEvent.Transcript("第一句\n最后一句", "", 2, "最后一句")), received)
    }
}
