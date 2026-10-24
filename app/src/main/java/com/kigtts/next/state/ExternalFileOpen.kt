package com.kigtts.next.state

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.kigtts.next.ui.plugins.PendingPackage
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

internal data class ExternalFilePrompt(val name: String, val kind: ExternalFileType = ExternalFileType.UNKNOWN,
    val busy: Boolean = false, val missing: List<String> = emptyList(), val error: String? = null,
    val candidate: PendingPackage? = null)

/** Own the temporary URI grant immediately; import only after native confirmation. */
internal class ExternalFileOpen(private val model: AppViewModel, private val scope: CoroutineScope) {
    private val context = model.getApplication<Application>()
    private val mutable = MutableStateFlow<ExternalFilePrompt?>(null)
    val prompt = mutable.asStateFlow()
    private var staged: File? = null
    private var pending: ExternalFilePrompt? = null
    private var waitingForPlugins = false
    private var job: Job? = null

    fun accept(intent: Intent?) {
        val uri = localUri(intent) ?: return
        if (job?.isActive == true || pending != null) { model.message("请先完成当前文件的导入"); return }
        mutable.value = ExternalFilePrompt("正在读取文件", busy = true)
        job = scope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    if (uri.scheme == "file") {
                        val file = File(requireNotNull(uri.path)).canonicalFile
                        require(file.toPath().startsWith(Environment.getExternalStorageDirectory().canonicalFile.toPath())) { "请通过文件管理器打开此文件" }
                    }
                    val name = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } }.getOrNull()?.take(200) ?: uri.lastPathSegment.orEmpty().take(200)
                    val file = File(context.cacheDir, "external-open/${UUID.randomUUID()}.package")
                    file.parentFile!!.mkdirs()
                    staged = file
                    requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取文件，请重新打开" }.use { input ->
                        file.outputStream().use { output ->
                            val bytes = com.kigtts.next.legacy.LegacyArchive.copy(input, output, 2L * 1024 * 1024 * 1024)
                            require(bytes > 0) { "文件为空" }
                        }
                    }
                    val kind = ExternalFileTypes.inspect(file, name)
                    require(kind != ExternalFileType.UNKNOWN) { "不支持此文件，请选择 KIGTTS 备份、语音包或插件包" }
                    ExternalFilePrompt(name.ifBlank { "导入文件" }, kind, candidate = if (kind == ExternalFileType.PLUGIN)
                        PendingPackage(file, model.pluginManager.packages.inspect(file)) else null)
                }
                pending = prepared
                showReady()
            } catch (cancel: CancellationException) { cleanup(); throw cancel }
            catch (failure: Exception) { cleanup(); mutable.value = ExternalFilePrompt("无法打开文件", error = failure.message ?: "请重新选择文件") }
        }
    }

    private fun missingVoicePlugins(): List<String> = buildList {
        if (model.registry.plugins.none { model.registry.available(it) && it.tts?.info?.role == TtsRole.SERVICE }) add("语音合成服务")
        if (model.registry.plugin("engine.piper")?.let { model.registry.available(it) && it.voiceManager != null } != true) add("Piper 语音引擎")
    }

    private fun showReady() {
        val current = pending ?: return
        mutable.value = if (current.kind == ExternalFileType.PIPER_VOICE) {
            if (!model.pluginCatalogReady) current.copy(busy = true)
            else current.copy(missing = missingVoicePlugins())
        } else current
    }

    fun pluginsChanged() {
        if (pending?.kind != ExternalFileType.PIPER_VOICE || job?.isActive == true) return
        if (!waitingForPlugins || missingVoicePlugins().isEmpty()) { waitingForPlugins = false; showReady() }
    }

    fun guide() {
        val missing = missingVoicePlugins().firstOrNull() ?: return showReady()
        waitingForPlugins = true
        mutable.value = null
        val installed = model.pluginManager.status.any {
            if (missing == "Piper 语音引擎") it.installed.manifest.id == "engine.piper"
            else "speech-service" in it.installed.manifest.capabilities
        }
        if (installed) { model.navigate(Destination.Plugins); return }
        // The store presents installation and publisher review; opening a file never installs dependencies.
        model.openSpeechPluginStore(missing)
    }

    fun resume() { if (waitingForPlugins) { waitingForPlugins = false; showReady() } }

    fun confirm() {
        val current = pending ?: return
        val file = staged ?: return
        if (job?.isActive == true) return
        if (current.kind == ExternalFileType.PIPER_VOICE && missingVoicePlugins().isNotEmpty()) return showReady()
        mutable.value = current.copy(busy = true)
        job = scope.launch {
            try {
                when (current.kind) {
                    ExternalFileType.QUICK_TEXT_BACKUP -> when (val result = model.quickTextBackup.prepareImport(context.contentResolver, Uri.fromFile(file))) {
                        is QuickTextReply.Prepared -> model.navigate(Destination.QuickTextReview(result.value.token))
                        is QuickTextReply.Failure -> error(result.message)
                        else -> error("无法准备导入")
                    }
                    ExternalFileType.LEGACY_BACKUP -> {
                        model.navigate(Destination.SettingsDetail(SettingsCategory.BACKUP_RESTORE))
                        requireNotNull(model.legacyImports.prepare(Uri.fromFile(file))) { "另一个备份正在导入，请稍后重试" }.join()
                    }
                    ExternalFileType.PIPER_VOICE -> importVoice(file)
                    else -> return@launch
                }
                cleanup(); mutable.value = null
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { mutable.value = current.copy(error = failure.message ?: "导入失败，请重试") }
        }
    }

    private suspend fun importVoice(file: File) {
        val engine = requireNotNull(model.registry.plugin("engine.piper")) { "请先启用 Piper 语音引擎" }
        val relative = "imports/voices/${UUID.randomUUID()}.kigvpk"
        val target = model.pluginManager.paths.storage(engine.descriptor.id).file(relative)
        try {
            withContext(Dispatchers.IO) { target.parentFile!!.mkdirs(); file.copyTo(target) }
            // RemoteVoiceManager requests the permission once, including one-time grants.
            val result = requireNotNull(engine.voiceManager).command(VoiceCommand(VoiceOperation.IMPORT, resourcePath = relative))
            when (result) {
                PluginOutcome.RefreshUi -> { model.refreshPlugins(); model.message("语音包已导入") }
                is PluginOutcome.Message -> error(result.text)
                else -> error("语音引擎未完成导入，请重试")
            }
        } finally { withContext(Dispatchers.IO) { target.delete() } }
    }

    fun dismiss() { if (job?.isActive != true) { cleanup(); mutable.value = null } }
    private fun cleanup() { staged?.delete(); staged = null; pending = null; waitingForPlugins = false }

    companion object {
        fun localUri(intent: Intent?): Uri? {
            if (intent?.action !in setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND)) return null
            @Suppress("DEPRECATION")
            val uri = if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                else intent?.data
            return uri?.takeIf { it.scheme in setOf("content", "file") }
        }
    }
}
