package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.*

internal class WorkerRecognitionBroker(private val remote: IHostSpeechBroker) : RecognitionBroker {
    override fun providers(): List<AsrProvider> = PluginJson.decodeFromString(remote.asrProviders())
    override suspend fun recognize(pluginId: String, audio: PcmAudio, config: RecognitionConfig): RecognitionResult =
        events(pluginId, flowOf(audio), config, false).filterIsInstance<RecognitionEvent.Final>().last().result
    override fun recognizeStream(pluginId: String, audio: Flow<PcmAudio>, config: RecognitionConfig): Flow<RecognitionEvent> =
        events(pluginId, audio, config, true)
    private fun events(id: String, audio: Flow<PcmAudio>, config: RecognitionConfig, streaming: Boolean) =
        recognitionPipe(audio, config, streaming, { input, live, settings, token -> remote.recognizeProvider(id, input, live, settings, token) }, remote::cancel)
}
