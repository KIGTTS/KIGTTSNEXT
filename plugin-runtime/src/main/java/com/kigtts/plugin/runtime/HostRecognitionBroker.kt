package com.kigtts.plugin.runtime

import android.os.ParcelFileDescriptor
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import java.io.DataOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Protocol-only ASR routing. A recognition service cannot call itself or another service. */
internal class HostRecognitionBroker(private val owner: String, private val registry: PluginRegistry, private val paths: PluginPaths) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class Session(val job: Job, val input: ParcelFileDescriptor, val output: ParcelFileDescriptor)
    private val sessions = ConcurrentHashMap<String, Session>()
    fun provider(id: String): KigPlugin {
        require(id != owner)
        return requireNotNull(registry.plugin(id)) { "识别引擎已停用或不存在" }.also {
            require(it.asr?.info?.role == AsrRole.PROVIDER) { "不能嵌套调用识别服务" }
        }
    }
    fun providers(): List<AsrProvider> {
        val registered = registry.plugins
        return registered.mapNotNull { plugin ->
            val descriptor = plugin.descriptor
            // A service can derive its info from this directory. Exclude it before reading info.
            if (descriptor.id == owner) return@mapNotNull null
            plugin.asr?.info?.takeIf { it.role == AsrRole.PROVIDER }?.let { info ->
                AsrProvider(descriptor.id, descriptor.name, info, settingsPages = plugin.engineSettingsPages())
            }
        } + ProviderCatalog(paths).disabledAsr(registered.map { it.descriptor.id }.toSet() + owner)
    }
    suspend fun control(id: String, command: AsrControl): Boolean =
        (provider(id).asr as? ControlledAsrEngine)?.control(command) ?: false
    fun recognize(id: String, input: ParcelFileDescriptor, streaming: Boolean, config: String, token: String): ParcelFileDescriptor {
        require(config.length <= 4096 && token.length in 1..80 && !sessions.containsKey(token))
        val engine = requireNotNull(provider(id).asr); val parsed = PluginJson.decodeFromString<RecognitionConfig>(config)
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])).use { output ->
                try { transferRecognition(input, output, engine, streaming, parsed) }
                catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { if (isActive) runCatching { AudioWire.error(output, error) } }
                finally { runCatching { input.close() }; sessions.remove(token) }
            }
        }
        sessions[token] = Session(job, input, pipe[1]); job.start(); return pipe[0]
    }
    fun cancel(token: String) { sessions.remove(token)?.let { runCatching { it.input.close() }; runCatching { it.output.close() }; it.job.cancel() } }
    fun close() { sessions.keys.toList().forEach(::cancel); scope.cancel() }
}
