package com.kigtts.plugin.runtime

import android.os.ParcelFileDescriptor
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Routes standard operations to provider processes. No engine-specific formats or settings here. */
internal class HostSpeechBroker(private val owner: String, private val registry: PluginRegistry,
    private val paths: PluginPaths, private val allowTts: Boolean = true, private val allowAsr: Boolean = false) : IHostSpeechBroker.Stub() {
    private val recognition = HostRecognitionBroker(owner, registry, paths)
    /** A built-in service calls the same authorized provider proxies without a second audio pipe. */
    fun builtinRecognition(): RecognitionBroker = object : RecognitionBroker {
        override fun providers(): List<AsrProvider> = recognition.providers()
        override suspend fun control(pluginId: String, command: AsrControl): Boolean = recognition.control(pluginId, command)
        override suspend fun recognize(pluginId: String, audio: PcmAudio, config: RecognitionConfig) =
            requireNotNull(recognition.provider(pluginId).asr).recognize(audio, config)
        override fun recognizeStream(pluginId: String, audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
            emitAll(requireNotNull(recognition.provider(pluginId).asr).recognizeStream(audio, config))
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class Session(val job: Job, val output: ParcelFileDescriptor)
    private val sessions = ConcurrentHashMap<String, Session>()
    private fun provider(id: String): KigPlugin {
        check(allowTts) { "插件未获授合成服务能力" }
        require(id != owner)
        return requireNotNull(registry.plugin(id)) { "引擎插件已停用或不存在" }.also {
            require(it.tts?.info?.role == TtsRole.PROVIDER) { "不能嵌套调用合成服务" }
        }
    }
    override fun providers(): String { check(allowTts); return PluginJson.encodeToString(registry.plugins.mapNotNull { plugin ->
        plugin.tts?.info?.takeIf { it.role == TtsRole.PROVIDER }?.let { TtsProvider(plugin.descriptor.id, plugin.descriptor.name, it,
            generation = (plugin as? RemotePlugin)?.generation.orEmpty(), settingsPages = plugin.engineSettingsPages()) }
    } + ProviderCatalog(paths).disabled(registry.plugins.map { it.descriptor.id }.toSet())) }
    override fun asrControl(pluginId: String, command: String): Boolean = runBlocking(Dispatchers.IO) {
        check(allowAsr) { "插件未获授识别服务能力" }
        require(command.length <= 8192)
        recognition.control(pluginId, PluginJson.decodeFromString(command))
    }
    override fun asrProviders(): String { check(allowAsr); return PluginJson.encodeToString(recognition.providers()) }
    override fun recognizeProvider(pluginId: String, input: ParcelFileDescriptor, streaming: Boolean, config: String, token: String): ParcelFileDescriptor {
        try {
            check(allowAsr) { "插件未获授识别服务能力" }
            return recognition.recognize(pluginId, input, streaming, config, token)
        } catch (error: Exception) { input.close(); throw error }
    }
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
            val forwarded = if (operation.operation in setOf(VoiceOperation.IMPORT, VoiceOperation.SET_AVATAR)) {
                val input = paths.storage(owner).file(requireNotNull(operation.resourcePath))
                require(input.isFile && input.length() in 1..MAX_RESOURCE_BYTES)
                val relative = "tmp/broker-${UUID.randomUUID()}.voicepack"
                staged = paths.storage(pluginId).file(relative).apply { parentFile!!.mkdirs() }
                input.inputStream().use { source -> staged!!.outputStream().use { source.copyTo(it) } }
                operation.copy(resourcePath = relative)
            } else operation
            val result = manager.command(forwarded)
            ProviderCatalog(paths).save(target)
            PluginJson.encodeToString(result)
        } finally { staged?.delete() }
    }
    override fun ttsControl(pluginId: String, command: String) = runBlocking(Dispatchers.IO) {
        require(command.length <= 8192)
        val engine = requireNotNull(provider(pluginId).tts)
        if (engine.info.supportsControl) engine.control(PluginJson.decodeFromString(command))
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
    override fun cancel(token: String) { recognition.cancel(token); sessions.remove(token)?.let { it.output.close(); it.job.cancel() } }
    fun close() { recognition.close(); sessions.keys.toList().forEach(::cancel); scope.cancel() }
}
