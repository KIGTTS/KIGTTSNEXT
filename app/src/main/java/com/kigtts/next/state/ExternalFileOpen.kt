package com.kigtts.next.state

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.kigtts.next.identity.ProfileArchive
import com.kigtts.next.identity.ProfileLongCardRestore
import com.kigtts.next.legacy.LegacyArchive
import com.kigtts.next.legacy.LegacyContactCards
import com.kigtts.next.ui.plugins.PendingPackage
import com.kigtts.next.ui.plugins.inspectPackage
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import java.util.UUID

internal data class ExternalFilePrompt(val name: String, val kind: ExternalFileType = ExternalFileType.UNKNOWN,
    val busy: Boolean = false, val missing: List<String> = emptyList(), val error: String? = null,
    val candidate: PendingPackage? = null, val dependency: PendingPackage? = null, val sharedClone: Boolean = false,
    val engineAction: VoiceImportAction? = null)

internal enum class VoiceImportAction { INSTALL, ENABLE }

/** Own the temporary URI grant immediately; import only after native confirmation. */
internal class ExternalFileOpen(private val model: AppViewModel, private val scope: CoroutineScope) {
    private val context = model.getApplication<Application>()
    private val mutable = MutableStateFlow<ExternalFilePrompt?>(null)
    val prompt = mutable.asStateFlow()
    private var staged: File? = null
    private var pending: ExternalFilePrompt? = null
    private var job: Job? = null
    private val chooseVoiceEvent = Channel<Unit>(Channel.CONFLATED)
    val chooseVoiceRequests = chooseVoiceEvent.receiveAsFlow()

    fun accept(intent: Intent?) {
        val uri = localUri(intent) ?: return
        model.sharing.dismissProfileFormat()
        acceptUri(uri, voiceOnly = false, mime = intent?.type)
    }

    fun chooseVoice() {
        if (job?.isActive == true || pending != null) model.message(model.hostCopy("resource.import_busy"))
        else chooseVoiceEvent.trySend(Unit)
    }

    fun voicePicked(uri: Uri) = acceptUri(uri, voiceOnly = true)
    fun profilePicked(uri: Uri) = acceptUri(uri, voiceOnly = false, profileOnly = true)

    private fun acceptUri(uri: Uri, voiceOnly: Boolean, profileOnly: Boolean = false, mime: String? = null) {
        if (job?.isActive == true || pending != null) { model.message(model.hostCopy("resource.import_busy")); return }
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
                    val fileMime = (mime ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()).orEmpty()
                    val expression = !voiceOnly && !profileOnly && (
                        fileMime == com.kigtts.next.camera.expressions.ExpressionPack.MIME ||
                            ExternalFileTypes.detect(name) == ExternalFileType.EXPRESSION)
                    val archive = ExternalFileTypes.detect(name) == ExternalFileType.PROFILE_ARCHIVE ||
                        fileMime in setOf(ProfileArchive.MIME, "message/rfc822", "application/x-mimearchive", "application/x-mhtml")
                    val photo = fileMime in setOf("image/png", "image/jpeg") ||
                        name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg")
                    val profileFile = !voiceOnly && (archive || photo || profileOnly)
                    val legacyCardName = name.endsWith(".kigcard", ignoreCase = true)
                    requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取文件，请重新打开" }.use { input ->
                        file.outputStream().use { output ->
                            val limit = when {
                                expression -> com.kigtts.next.camera.expressions.ExpressionPack.MAX_PACK.toLong()
                                archive -> ProfileArchive.MAX_BYTES
                                profileOnly && legacyCardName -> LegacyArchive.MAX_BYTES
                                profileFile -> ProfileLongCardRestore.MAX_BYTES
                                ExternalFileTypes.detect(name) == ExternalFileType.LEGACY_BACKUP -> LegacyArchive.MAX_BYTES
                                else -> 2L * 1024 * 1024 * 1024
                            }
                            val bytes = com.kigtts.next.legacy.LegacyArchive.copy(input, output, limit)
                            require(bytes > 0) { "文件为空" }
                        }
                    }
                    val image = profileFile && ProfileLongCardRestore.imageFile(file)
                    val kind = when {
                        expression -> {
                            com.kigtts.next.camera.expressions.ExpressionLibrary.read(file)
                            ExternalFileType.EXPRESSION
                        }
                        image -> ExternalFileType.UNKNOWN
                        else -> ExternalFileTypes.inspect(file, name)
                    }
                    val legacyCard = kind == ExternalFileType.LEGACY_BACKUP && (profileOnly || legacyCardName)
                    require(!voiceOnly || kind.isVoice) { "无法识别此语音包，请选择由受支持的语音引擎导出的语音包" }
                    require(!profileFile || image || legacyCard || kind == ExternalFileType.PROFILE_ARCHIVE) { "没有找到可恢复的 KIGTTS 名片数据" }
                    require(kind != ExternalFileType.UNKNOWN || image) { "不支持此文件，请选择 KIGTTS 备份、语音包或插件包" }
                    if (kind == ExternalFileType.PROFILE_ARCHIVE || image || legacyCard) {
                        val scratch = File(file.parentFile, "${file.nameWithoutExtension}-profile")
                        try {
                            when {
                                image -> requireNotNull(ProfileLongCardRestore.restore(file, scratch, model.contactFavorites)) {
                                    "这张图没有可恢复的 KIGTTS 名片数据"
                                }
                                legacyCard -> {
                                    val content = LegacyArchive.read(file, scratch)
                                    require(content.cardPackage && content.cards.isNotEmpty()) {
                                        model.hostCopy("backup.legacy_card_invalid")
                                    }
                                    LegacyContactCards(context).restore(content.cards)
                                }
                                else -> ProfileArchive.restore(file, scratch, model.contactFavorites)
                            }
                        }
                        finally { scratch.deleteRecursively() }
                        null
                    } else ExternalFilePrompt(name.ifBlank { "导入文件" }, kind, candidate = if (kind == ExternalFileType.PLUGIN)
                        PendingPackage(file, model.pluginManager.packages.inspect(file)) else null)
                }
                if (prepared == null) {
                    cleanup()
                    mutable.value = null
                    val navigation = model.state.value
                    if (navigation.destination is Destination.ContactFavoriteDetail &&
                        navigation.navigationStack.lastOrNull() == Destination.ContactFavorites) model.navigateBack()
                    else if (navigation.destination != Destination.ContactFavorites) model.navigate(Destination.ContactFavorites)
                    model.message(model.hostCopy("profile.added_to_collection"))
                    return@launch
                }
                pending = prepared
                showReady()
            } catch (cancel: CancellationException) { cleanup(); throw cancel }
            catch (failure: Exception) { cleanup(); mutable.value = ExternalFilePrompt("无法打开文件", error = failure.message ?: "请重新选择文件") }
        }
    }

    private fun missingVoicePlugins(kind: ExternalFileType): List<String> = buildList {
        if (model.registry.plugins.none { model.registry.available(it) && it.tts?.info?.role == TtsRole.SERVICE }) add("语音合成服务")
        if (!engineReady(kind)) add(requireNotNull(kind.engineName))
    }

    private fun engineReady(kind: ExternalFileType) =
        kind.engineId?.let(model.registry::plugin)?.let { model.registry.available(it) && it.voiceManager != null } == true

    private fun resolveVoiceEngine(kind: ExternalFileType): ExternalFileType {
        if (kind != ExternalFileType.SHARED_CLONE_VOICE) return kind
        // Early clone exports use an interchangeable reference-audio format with no engine ID.
        return cloneEngines.firstOrNull(::engineReady)
            ?: cloneEngines.firstOrNull { engine -> model.pluginManager.status.any { it.installed.manifest.id == engine.engineId } }
            ?: ExternalFileType.CLONE_VOICE
    }

    private fun showReady(error: String? = null) {
        val item = pending ?: return
        val current = item.copy(kind = resolveVoiceEngine(item.kind), sharedClone = item.kind == ExternalFileType.SHARED_CLONE_VOICE)
        mutable.value = if (current.kind.isVoice) {
            if (!model.pluginCatalogReady) current.copy(busy = true)
            else {
                val engine = model.pluginManager.status.firstOrNull { it.installed.manifest.id == current.kind.engineId }
                val action = when {
                    engineReady(current.kind) -> null
                    engine == null -> VoiceImportAction.INSTALL
                    !engine.installed.enabled -> VoiceImportAction.ENABLE
                    else -> null
                }
                current.copy(missing = missingVoicePlugins(current.kind), error = error, engineAction = action)
            }
        } else current.copy(error = error)
    }

    fun pluginsChanged() {
        if (pending?.kind?.isVoice != true || job?.isActive == true || mutable.value?.dependency != null) return
        showReady()
    }

    fun enableEngine() {
        val current = mutable.value ?: return
        val id = current.kind.engineId ?: return
        if (current.engineAction != VoiceImportAction.ENABLE || job?.isActive == true) return
        mutable.value = current.copy(busy = true)
        job = scope.launch {
            try {
                model.pluginManager.enable(id, true)
                model.refreshPlugins()
                showReady()
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { showReady(failure.message ?: "无法启用语音引擎") }
        }
    }

    fun dependencyPicked(uri: Uri) {
        val current = mutable.value ?: return
        val id = current.kind.engineId ?: return
        if (current.engineAction != VoiceImportAction.INSTALL || job?.isActive == true) return
        mutable.value = current.copy(busy = true, error = null)
        job = scope.launch {
            var inspected: PendingPackage? = null
            try {
                val candidate = inspectPackage(context, model, uri)
                inspected = candidate
                val manifest = candidate.preview.manifest
                val matching = manifest.id == id || current.sharedClone && cloneEngines.any { it.engineId == manifest.id }
                require(matching && manifest.capabilities.containsAll(listOf("tts", "voice-packs"))) {
                    "此安装包与所选语音包不兼容"
                }
                mutable.value = current.copy(dependency = candidate, error = null)
                inspected = null
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { showReady(failure.message ?: "无法读取引擎安装包") }
            finally { inspected?.file?.delete() }
        }
    }

    fun finishDependencyInstaller() {
        mutable.value?.dependency?.file?.delete()
        showReady()
    }

    fun confirm() {
        val current = mutable.value?.takeUnless { it.busy || it.dependency != null } ?: return
        if (pending == null) return
        val file = staged ?: return
        if (job?.isActive == true) return
        if (current.kind.isVoice && missingVoicePlugins(current.kind).isNotEmpty()) return showReady()
        mutable.value = current.copy(busy = true)
        job = scope.launch {
            try {
                when (current.kind) {
                    ExternalFileType.QUICK_TEXT_BACKUP, ExternalFileType.LEGACY_BACKUP -> {
                        model.backupRestore.offerFile(file)
                        model.navigate(Destination.SettingsDetail(SettingsCategory.BACKUP_RESTORE))
                    }
                    ExternalFileType.PIPER_VOICE, ExternalFileType.CLONE_VOICE, ExternalFileType.SHARED_CLONE_VOICE,
                    ExternalFileType.QWEN_VOICE -> importVoice(file, current.kind)
                    ExternalFileType.EXPRESSION -> {
                        withContext(Dispatchers.IO) {
                            com.kigtts.next.camera.expressions.ExpressionLibrary.get(context).install(file)
                        }
                        model.message(model.hostCopy("expression.installed"))
                    }
                    else -> return@launch
                }
                cleanup(); mutable.value = null
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                showReady(if (current.kind == ExternalFileType.EXPRESSION) model.hostCopy("expression.file_invalid")
                    else failure.message ?: "导入失败，请重试")
            }
        }
    }

    internal suspend fun importVoice(file: File, kind: ExternalFileType? = null) {
        val type = resolveVoiceEngine(kind ?: withContext(Dispatchers.IO) { ExternalFileTypes.inspect(file, file.name) })
        require(type.isVoice) { "无法识别此语音包" }
        val engine = requireNotNull(type.engineId?.let(model.registry::plugin)) { "请先启用${type.engineName}" }
        val suffix = if (type == ExternalFileType.PIPER_VOICE) "kigvpk" else "zip"
        val relative = "imports/voices/${UUID.randomUUID()}.$suffix"
        val target = model.pluginManager.paths.storage(engine.descriptor.id).file(relative)
        try {
            withContext(Dispatchers.IO) { target.parentFile!!.mkdirs(); file.copyTo(target) }
            // RemoteVoiceManager requests the permission once, including one-time grants.
            val result = requireNotNull(engine.voiceManager).command(VoiceCommand(VoiceOperation.IMPORT, resourcePath = relative))
            when (result) {
                PluginOutcome.RefreshUi -> { model.refreshPlugins(); model.message(model.hostCopy("resource.voice_imported")) }
                is PluginOutcome.Message -> error(result.text)
                else -> error("语音引擎未完成导入，请重试")
            }
        } finally { withContext(Dispatchers.IO) { target.delete() } }
    }

    fun dismiss() { if (job?.isActive != true) { cleanup(); mutable.value = null } }
    private fun cleanup() {
        mutable.value?.dependency?.file?.delete()
        staged?.delete()
        staged = null
        pending = null
    }

    companion object {
        private val cloneEngines = listOf(ExternalFileType.CLONE_VOICE, ExternalFileType.QWEN_VOICE)
        fun localUri(intent: Intent?): Uri? {
            if (intent?.action !in setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND)) return null
            @Suppress("DEPRECATION")
            val uri = if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                else intent?.data
            return uri?.takeIf { it.scheme in setOf("content", "file") }
        }
    }
}
