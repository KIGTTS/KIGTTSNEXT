package com.kigtts.next.resources

import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

internal data class ResourceInstallPrompt(val token: String, val archive: ResourceArchive, val busy: Boolean = false, val error: String? = null)
private data class ResourceReceipt(val owner: String, val state: String, val message: String = "")

/** Caller-bound receipts report real import results, independently of plugin installation metadata. */
class ResourceInstalls(private val model: AppViewModel, private val scope: CoroutineScope) {
    private val lock = Mutex()
    private val receipts = java.util.concurrent.ConcurrentHashMap<String, ResourceReceipt>()
    private val mutable = MutableStateFlow<ResourceInstallPrompt?>(null)
    @Volatile private var importing: Job? = null
    internal val prompt = mutable.asStateFlow()
    suspend fun request(owner: String, command: AppCommand.ResourcePackage): AppReply = lock.withLock {
        if (command.operation == "status") return@withLock status(owner, requireNotNull(command.token))
        require(command.operation == "install")
        if (mutable.value != null || importing?.isActive == true) return@withLock AppReply.Failure("BUSY", "请先完成当前资源的导入")
        val source = requireNotNull(command.source)
        require(source.pluginId == null || source.pluginId == owner) { "只能导入插件自己的资源" }
        val file = requireNotNull(model.pluginManager.paths.existingResource(owner, source.path))
        val token = UUID.randomUUID().toString()
        val directory = File(model.getApplication<android.app.Application>().cacheDir, "kpm-imports/$token")
        val archive = withContext(Dispatchers.IO) { ResourceArchive.read(file, directory, requireNotNull(command.expectedPublisher)) }
        receipts[token] = ResourceReceipt(owner, "PENDING")
        while (receipts.size > 128) receipts.remove(receipts.keys.first())
        withContext(Dispatchers.Main) { mutable.value = ResourceInstallPrompt(token, archive) }
        status(owner, token)
    }
    private fun status(owner: String, token: String): AppReply {
        val receipt = receipts[token]?.takeIf { it.owner == owner } ?: return AppReply.Failure("NOT_FOUND", "导入记录不存在")
        return AppReply.Data(buildJsonObject { put("token", token); put("state", receipt.state); put("message", receipt.message) })
    }
    internal fun missingVoice(): Boolean = mutable.value?.archive?.manifest?.resourceType == ResourceType.VOICE &&
        (model.registry.plugin("engine.piper")?.let { model.registry.available(it) && it.voiceManager != null } != true ||
            model.registry.plugins.none { model.registry.available(it) && it.tts?.info?.role == TtsRole.SERVICE })
    internal fun guide() {
        val prompt = mutable.value ?: return
        receipts[prompt.token]?.let { receipts[prompt.token] = it.copy(state = "DEFERRED") }
        mutable.value = null; discard(prompt)
        model.openSpeechPluginStore(if (model.registry.plugins.none { model.registry.available(it) && it.tts?.info?.role == TtsRole.SERVICE }) "语音合成服务" else "Piper 语音引擎")
    }
    internal fun dismiss() {
        if (mutable.value?.busy == true) { mutable.value = null; return }
        val prompt = mutable.value?.takeUnless { it.busy } ?: return
        receipts[prompt.token]?.let { receipts[prompt.token] = it.copy(state = "CANCELLED") }
        mutable.value = null; discard(prompt)
    }
    private fun discard(prompt: ResourceInstallPrompt) {
        scope.launch(Dispatchers.IO) { runCatching { prompt.archive.directory.deleteRecursively() } }
    }
    internal fun confirm() {
        val prompt = mutable.value?.takeUnless { it.busy } ?: return
        if (missingVoice()) { guide(); return }
        mutable.value = prompt.copy(busy = true, error = null)
        importing = scope.launch {
            try {
                importResource(model, prompt.archive)
                receipts[prompt.token]?.let { receipts[prompt.token] = it.copy(state = "COMPLETED") }
                withContext(Dispatchers.IO) { runCatching { prompt.archive.directory.deleteRecursively() } }
                if (mutable.value?.token == prompt.token) mutable.value = null
                model.message(model.hostCopy("resource.added", "name" to prompt.archive.manifest.name))
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) {
                val message = model.hostCopy("resource.import_failed")
                receipts[prompt.token]?.let { receipts[prompt.token] = it.copy(state = "FAILED", message = message) }
                if (mutable.value?.token == prompt.token) mutable.value = prompt.copy(error = message)
                else { discard(prompt); model.message(message) }
            }
        }
    }
}
