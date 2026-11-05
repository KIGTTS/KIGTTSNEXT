package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Built-in services already run in the host. External engines own their one IPC stream. */
internal class BuiltinSpeechBroker(private val owner: String, private val registry: PluginRegistry,
    broker: HostSpeechBroker) : SpeechBroker by WorkerSpeechBroker(broker) {
    override fun synthesize(pluginId: String, request: TtsRequest) = flow {
        require(pluginId != owner) { "不能循环调用朗读服务" }
        val engine = requireNotNull(registry.plugin(pluginId)?.tts) { "引擎已停用或不存在" }
        require(engine.info.role == TtsRole.PROVIDER) { "不能嵌套调用合成服务" }
        emitAll(engine.synthesize(request))
    }
}
