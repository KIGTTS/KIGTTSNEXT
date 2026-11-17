package com.kigtts.engines.recognitionservice

import android.speech.SpeechRecognizer
import com.kigtts.plugin.api.EngineError
import com.kigtts.plugin.api.EngineException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull

internal class SystemRecognitionStarting : Exception()

/** A transient rejection before Android starts listening can be retried without recording twice. */
internal fun shouldRetrySystemRecognition(error: Int, ready: Boolean): Boolean = !ready && when (error) {
    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> true
    else -> false
}

internal suspend fun retrySystemRecognitionStartup(
    stop: Deferred<Unit>,
    notReadyMessage: () -> String,
    start: suspend () -> String,
): String {
    val retryDelays = longArrayOf(250, 550)
    for (attempt in 0..retryDelays.size) {
        if (stop.isCompleted) return ""
        try {
            return start()
        } catch (_: SystemRecognitionStarting) {
            if (stop.isCompleted) return ""
            if (attempt == retryDelays.size) throw EngineException(EngineError.UNAVAILABLE, notReadyMessage())
            withTimeoutOrNull(retryDelays[attempt]) { stop.await() }
        }
    }
    return ""
}
