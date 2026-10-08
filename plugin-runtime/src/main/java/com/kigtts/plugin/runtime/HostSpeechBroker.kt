package com.kigtts.plugin.runtime

import android.os.ParcelFileDescriptor
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Routes standard operations to provider processes. No engine-specific formats or settings here. */
internal class HostSpeechBroker(private val owner: String, private val registry: PluginRegistry,
    private val paths: PluginPaths) : IHostSpeechBroker.Stub() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class Session(val job: Job, val output: ParcelFileDescriptor)
    private val sessions = ConcurrentHashMap<String, Session>()
    private fun provider(id: String): KigPlugin {
        require(id != owner)
        return requireNotNull(registry.plugin(id)) { "引擎插件已停用或不存在" }.also {
            require(it.tts?.info?.role == TtsRole.PROVIDER) { "不能嵌套调用合成服务" }
        }
    }
    override fun providers(): String = PluginJson.encodeToString(registry.plugins.mapNotNull { plugin ->
        plugin.tts?.info?.takeIf { it.role == TtsRole.PROVIDER }?.let { TtsProvider(plugin.descriptor.id, plugin.descriptor.name, it) }
    })
    override fun command(pluginId: String, command: String): String = runBlocking(Dispatchers.IO) {
        require(command.length <= 8192)
        val operation = PluginJson.decodeFromString<VoiceCommand>(command)
        val target = provider(pluginId)
        if (operation.operation == VoiceOperation.REFRESH && target.voiceManager == null) {
            (target as? RemotePlugin)?.refresh(PluginContext("", ""))
            return@runBlocking PluginJson.encodeToString<PluginOutcome>(PluginOutcome.RefreshUi)
        }
        val manager = requireNotNull(target.voiceManager) { "该引擎不支持语音包管理" }
        var staged: java.io.File? = null
        try {
            val forwarded = if (operation.operation == VoiceOperation.IMPORT) {
                val input = paths.storage(owner).file(requireNotNull(operation.resourcePath))
                require(input.isFile && input.length() in 1..MAX_RESOURCE_BYTES)
                val relative = "tmp/broker-${UUID.randomUUID()}.voicepack"
                staged = paths.storage(pluginId).file(relative).apply { parentFile!!.mkdirs() }
                input.inputStream().use { source -> staged!!.outputStream().use { source.copyTo(it) } }
                operation.copy(resourcePath = relative)
            } else operation
            PluginJson.encodeToString(manager.command(forwarded))
        } finally { staged?.delete() }
    }
    override fun synthesize(pluginId: String, request: String, token: String): ParcelFileDescriptor {
        require(token.length in 1..80 && !sessions.containsKey(token))
        val engine = requireNotNull(provider(pluginId).tts)
        val parsed = PluginJson.decodeFromString<TtsRequest>(request)
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])).use { output ->
                try {
                    engine.synthesize(parsed).collect { event -> when (event) {
                        SynthesisEvent.Started -> { output.writeInt(AudioWire.START); output.flush() }
                        is SynthesisEvent.Audio -> AudioWire.pcm(output, event.audio)
                        is SynthesisEvent.Finished -> AudioWire.text(output, AudioWire.FINISH, event.notice.orEmpty())
                    } }
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { if (isActive) runCatching { AudioWire.error(output, error) } }
                finally { sessions.remove(token) }
            }
        }
        sessions[token] = Session(job, pipe[1]); job.start()
        return pipe[0]
    }
    override fun cancel(token: String) { sessions.remove(token)?.let { it.output.close(); it.job.cancel() } }
    fun close() { sessions.keys.toList().forEach(::cancel); scope.cancel() }
}
