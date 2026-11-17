package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.Flow

internal const val SYSTEM_RECOGNITION_ID = "host.system-asr"

internal fun systemRecognitionUnavailable() = "未找到可用的系统识别服务"

/** A built-in choice, owned by the service rather than installed or started as a plugin. */
internal class SystemRecognitionBroker(private val plugins: RecognitionBroker, private val system: AsrEngine) : RecognitionBroker {
    override fun providers() = listOf(AsrProvider(SYSTEM_RECOGNITION_ID, "系统识别引擎", system.info)) +
        plugins.providers().filterNot { it.pluginId == SYSTEM_RECOGNITION_ID }

    override suspend fun recognize(pluginId: String, audio: PcmAudio, config: RecognitionConfig) =
        if (pluginId == SYSTEM_RECOGNITION_ID) system.recognize(audio, config) else plugins.recognize(pluginId, audio, config)

    override fun recognizeStream(pluginId: String, audio: Flow<PcmAudio>, config: RecognitionConfig) =
        if (pluginId == SYSTEM_RECOGNITION_ID) system.recognizeStream(audio, config) else plugins.recognizeStream(pluginId, audio, config)

    // Android owns the underlying model lifetime; we open a recognizer only for a request.
    override suspend fun control(pluginId: String, command: AsrControl) =
        if (pluginId == SYSTEM_RECOGNITION_ID) false else plugins.control(pluginId, command)
}
