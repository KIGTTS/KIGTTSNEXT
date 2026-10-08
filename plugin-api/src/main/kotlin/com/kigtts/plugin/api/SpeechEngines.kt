package com.kigtts.plugin.api

import kotlinx.coroutines.flow.Flow

/** Signed 16-bit little-endian PCM. Chunk boundaries must align with a full sample frame. */
data class PcmAudio(val bytes: ByteArray, val sampleRate: Int = 16_000, val channels: Int = 1) {
    init {
        require(sampleRate > 0 && channels in 1..2)
        require(bytes.size % (2 * channels) == 0) { "PCM must contain complete sample frames" }
    }
}

data class TtsRequest(val text: String, val language: String = "zh-CN", val rate: Float = 1f)
sealed interface SynthesisEvent {
    data object Started : SynthesisEvent
    data class Audio(val audio: PcmAudio) : SynthesisEvent
    data class Finished(val notice: String? = null) : SynthesisEvent
}

interface TtsEngine {
    /** Cold flow; cancelling its collection must release engine resources. */
    fun synthesize(request: TtsRequest): Flow<SynthesisEvent>
}

data class RecognitionConfig(val language: String = "zh-CN")
data class RecognitionResult(val text: String, val placeholder: Boolean = false)
sealed interface RecognitionEvent {
    data object Started : RecognitionEvent
    data class Partial(val text: String) : RecognitionEvent
    data class Final(val result: RecognitionResult) : RecognitionEvent
}

interface AsrEngine {
    /** One complete recording, one result. Cancellation propagates to the engine. */
    suspend fun recognize(audio: PcmAudio, config: RecognitionConfig = RecognitionConfig()): RecognitionResult
    /** Cold flow consuming PCM chunks with backpressure. Input completion finalizes the session. */
    fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig = RecognitionConfig()): Flow<RecognitionEvent>
}
