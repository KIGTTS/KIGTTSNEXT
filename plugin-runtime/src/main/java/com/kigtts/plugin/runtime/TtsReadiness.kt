package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Only generic control signals live in the host. Native state and idle policy belong to plugins. */
internal class TtsReadiness(private val registry: PluginRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
    private val changes = Channel<PluginContext>(Channel.CONFLATED)
    init { scope.launch {
        var previous: KigPlugin? = null
        var automatic = false
        var active = false
        for (context in changes) {
            val selected = registry.plugin(context.ttsPluginId.orEmpty())
            val switched = selected !== previous
            if (switched) {
                previous?.tts?.takeIf { it.info.supportsControl }?.let { engine ->
                    runCatching { engine.control(TtsControl(TtsControlOperation.RELEASE)) }
                }
            }
            if ((switched || !automatic) && context.automaticRead) {
                selected?.tts?.takeIf { it.info.supportsControl }?.let { engine ->
                    runCatching { engine.control(TtsControl(TtsControlOperation.PREPARE)) }
                }
            }
            if (switched && context.speechActive || active != context.speechActive) {
                selected?.tts?.takeIf { it.info.supportsControl }?.let { engine ->
                    runCatching { engine.control(TtsControl(TtsControlOperation.ACTIVITY, active = context.speechActive)) }
                }
            }
            previous = selected; automatic = context.automaticRead; active = context.speechActive
        }
    } }
    fun observe(context: PluginContext) { changes.trySend(context) }
    fun close() { changes.close(); scope.cancel() }
}
