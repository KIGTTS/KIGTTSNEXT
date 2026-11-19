package com.kigtts.engines.recognitionservice

import android.speech.SpeechRecognizer
import com.kigtts.plugin.api.RecognitionConfig
import com.kigtts.plugin.api.RecognitionEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCaptionSessionsTest {
    @Test fun transientStartupErrorCanRecoverWithoutShowingAnError() = runTest {
        val stop = CompletableDeferred<Unit>()
        var requests = 0
        val result = retrySystemRecognitionStartup(stop, { error("unexpected error") }) {
            if (++requests == 1) throw SystemRecognitionStarting()
            "识别成功"
        }
        assertEquals("识别成功", result)
        assertEquals(2, requests)
        assertTrue(shouldRetrySystemRecognition(SpeechRecognizer.ERROR_CLIENT, ready = false))
        assertFalse(shouldRetrySystemRecognition(SpeechRecognizer.ERROR_CLIENT, ready = true))
        assertFalse(shouldRetrySystemRecognition(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, ready = false))
    }

    @Test fun releaseWhileTheSystemStartsDoesNotLaunchAnotherRequest() = runTest {
        val stop = CompletableDeferred<Unit>()
        var requests = 0
        launch { delay(50); stop.complete(Unit) }
        val result = retrySystemRecognitionStartup(stop, { error("unexpected error") }) {
            requests++
            throw SystemRecognitionStarting()
        }
        assertEquals("", result)
        assertEquals(1, requests)
    }

    @Test fun liveCaptionsCanRecoverFromATemporaryInterruptionAfterTheRecognizerWasReady() {
        assertTrue(shouldRetrySystemRecognition(SpeechRecognizer.ERROR_SERVER_DISCONNECTED, ready = true, continuousCaptions = true))
        assertTrue(shouldRetrySystemRecognition(SpeechRecognizer.ERROR_AUDIO, ready = true, continuousCaptions = true))
        assertTrue(shouldRetrySystemRecognition(SpeechRecognizer.ERROR_NETWORK_TIMEOUT, ready = true, continuousCaptions = true))
        assertTrue(shouldRetrySystemRecognition(SpeechRecognizer.ERROR_SERVER, ready = false, continuousCaptions = true))
        assertFalse(shouldRetrySystemRecognition(SpeechRecognizer.ERROR_AUDIO, ready = true))
        assertFalse(shouldRetrySystemRecognition(SpeechRecognizer.ERROR_NETWORK_TIMEOUT, ready = false))
        assertFalse(shouldRetrySystemRecognition(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, ready = true, continuousCaptions = true))
    }

    @Test fun liveCaptionsKeepPriorLinesAndCanRetryLongerThanVoiceInput() = runTest {
        val stop = CompletableDeferred<Unit>()
        var sentence = 0
        var attempts = 0
        val received = systemCaptionSessions(stop, RecognitionConfig()) {
            if (++sentence == 1) "第一句"
            else retrySystemRecognitionStartup(stop, { error("unexpected error") }, continuousCaptions = true) {
                if (++attempts <= 3) throw SystemRecognitionStarting()
                stop.complete(Unit)
                "恢复后的句子"
            }
        }.toList()
        assertEquals(4, attempts)
        assertEquals(RecognitionEvent.Transcript("第一句\n恢复后的句子", "", 2, "恢复后的句子"), received.last())
    }

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
