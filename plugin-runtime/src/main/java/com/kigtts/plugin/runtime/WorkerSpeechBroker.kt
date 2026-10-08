package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WorkerSpeechBroker(private val remote: IHostSpeechBroker) : SpeechBroker {
    override fun providers(): List<TtsProvider> = PluginJson.decodeFromString(remote.providers())
    override suspend fun command(pluginId: String, command: VoiceCommand): PluginOutcome = withContext(Dispatchers.IO) {
        PluginJson.decodeFromString(remote.command(pluginId, PluginJson.encodeToString(command)))
    }
    override fun synthesize(pluginId: String, request: TtsRequest) =
        synthesisPipe({ token -> remote.synthesize(pluginId, PluginJson.encodeToString(request), token) }, { token -> remote.cancel(token) })
}
