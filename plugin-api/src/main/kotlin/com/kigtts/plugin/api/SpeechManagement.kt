package com.kigtts.plugin.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class TtsProvider(val pluginId: String, val name: String, val info: TtsInfo)

@Serializable
enum class VoiceOperation { IMPORT, SELECT, DELETE, RENAME, REFRESH }
@Serializable
data class VoiceCommand(val operation: VoiceOperation, val voiceId: String? = null,
    val resourcePath: String? = null, val value: String? = null)

/** Each provider parses, validates and stores its own packs. Host never knows a voice file format. */
interface VoiceManager {
    suspend fun command(command: VoiceCommand): PluginOutcome
}

/** Granted only to a synthesis service. Calls are mediated by the host; no implementation is shared. */
interface SpeechBroker {
    fun providers(): List<TtsProvider>
    suspend fun command(pluginId: String, command: VoiceCommand): PluginOutcome
    fun synthesize(pluginId: String, request: TtsRequest): Flow<SynthesisEvent>
}
