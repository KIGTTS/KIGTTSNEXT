package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.RecognitionConfig
import com.kigtts.plugin.api.RecognitionEvent
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/** A device recognizer is started for one utterance at a time; stop never starts another request. */
internal fun systemCaptionSessions(stop: Deferred<Unit>, config: RecognitionConfig, utterance: suspend () -> String) = flow {
    val transcript = TranscriptAssembler(config.maxDisplayCharacters)
    emit(RecognitionEvent.Started)
    while (!stop.isCompleted) {
        transcript.finalize(utterance())?.let { emit(it) }
        // Give the vendor service time to release its previous microphone lease.
        if (!stop.isCompleted) withTimeoutOrNull(180) { stop.await() }
    }
}
