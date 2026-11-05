package com.kigtts.next.sharing

import android.app.Application
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.identity.ProfilePdfExport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Optional providers receive only the files the user explicitly chose to share. */
internal class SharingCoordinator(private val model: AppViewModel, private val scope: CoroutineScope) {
    val deviceKey = UUID.randomUUID().toString().replace("-", "")
    private val context get() = model.getApplication<Application>()
    val setup: CloudSharingSetup = CloudSharingSetup(context.getSharedPreferences("sharing", 0), scope,
        ready = { provider("sharing/open-v1") != null },
        installed = { model.pluginManager.status.any { it.installed.manifest.id == CLOUD_SHARING_ID } },
        install = { progress ->
            val existing = model.pluginManager.status.firstOrNull { it.installed.manifest.id == CLOUD_SHARING_ID }
            // A failed package must be replaced; re-enabling it cannot repair its bytecode.
            if (existing == null || existing.error != null) {
                val (file, preview) = withContext(Dispatchers.IO) {
                    CloudSharingDownload(model.pluginManager.paths.packages, model.pluginManager.packages::inspect).download(progress)
                }
                progress(null)
                try { model.pluginManager.install(file, preview.publisherSha256) }
                finally { withContext(Dispatchers.IO) { file.delete() } }
            } else {
                progress(null)
                model.pluginManager.enable(CLOUD_SHARING_ID, true)
            }
            check(model.pluginManager.status.firstOrNull { it.installed.manifest.id == CLOUD_SHARING_ID }?.error == null) {
                "云共享未能启动，请重试或使用系统分享"
            }
            model.pluginManager.status.firstOrNull { it.installed.manifest.id == CLOUD_SHARING_ID }?.let {
                model.pluginManager.permissions.set("$CLOUD_SHARING_ID:${it.installed.publisherSha256}", "network", com.kigtts.plugin.runtime.PermissionAccess.ALLOW)
            }
            model.refreshPlugins()
        })
    fun close() { setup.close(); preparing?.cancel() }
    private var preparing: Job? = null
    private var invitation: String? = null
    private val mutableNfc = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val nfc = mutableNfc.asStateFlow()
    private val mutablePanel = kotlinx.coroutines.flow.MutableStateFlow<CloudNativePanel?>(null)
    val nativePanel = mutablePanel.asStateFlow()
    fun dismissNative() { mutablePanel.value = null }
    fun nativePage(owner: String, pageId: String): CloudNativePanel? {
        if (!isProvider(owner) || "sharing/native-v1" !in model.registry.plugin(owner)!!.contributions.automationActions) return null
        val block = model.pluginPage(owner, pageId)?.blocks?.filterIsInstance<com.kigtts.plugin.api.PageBlock.Browser>()?.singleOrNull() ?: return null
        if (block.mainPage != "native.html" || block.root == null || block.url != null) return null
        return CloudNativePanel(owner, pageId, block)
    }
    fun showNative(owner: String, pageId: String): Boolean {
        val panel = nativePage(owner, pageId) ?: return false
        mutablePanel.value = panel; return true
    }
    fun dismissNfc() { mutableNfc.value = null }
    fun accept(intent: android.content.Intent?): Boolean = CloudShareLinks.from(intent)?.let(::open) ?: false
    fun open(raw: String): Boolean {
        val url = CloudShareLinks.parse(raw) ?: return false
        invitation = url; deliver(); return true
    }
    fun deliver() {
        if (!model.pluginCatalogReady) return
        val url = invitation ?: return
        val provider = provider("sharing/open-v1")
        if (provider?.descriptor?.id in model.state.value.busyPlugins) return
        invitation = null
        if (provider != null) model.invoke(provider.descriptor.id, "sharing/open-v1", mapOf("url" to url))
        else scope.launch {
            if (setup.ensure()) provider("sharing/open-v1")?.let { model.invoke(it.descriptor.id, "sharing/open-v1", mapOf("url" to url)) }
            else model.message("请先启用云共享，再打开这份分享")
        }
    }
    fun handle(owner: String, action: String, inputs: Map<String, String>): Boolean {
        if (action !in setOf("sharing/system-link-v1", "sharing/nfc-v1")) return false
        require(isProvider(owner)) { "分享提供方不可用" }
        val url = requireNotNull(CloudShareLinks.parse(inputs["url"].orEmpty())) { "分享链接无效" }
        if (action == "sharing/nfc-v1") mutableNfc.value = url
        else context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, url)
        }, "分享链接").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
    fun isProvider(owner: String) = model.registry.plugin(owner)?.let {
        model.registry.available(it) && "sharing/publish-v1" in it.contributions.automationActions
    } == true
    fun received(owner: String, file: File, path: String) = scope.launch {
        try {
            require(isProvider(owner))
            if (file.extension == "kigcard") {
                model.quickTextBackup.offerImport(context.contentResolver, android.net.Uri.fromFile(file))
                model.navigate(com.kigtts.next.state.Destination.SettingsDetail(com.kigtts.next.state.SettingsCategory.BACKUP_RESTORE))
            } else if (file.extension.lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "heic", "heif")) {
                com.kigtts.next.media.saveGalleryImage(context, file); model.message("已保存到系统相册")
            } else { model.invoke(owner, "sharing/received-v1", mapOf("resourcePath" to path)) }
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: Exception) { model.message(error.message ?: "保存失败，请重试")
        } finally { if (file.extension.lowercase() in setOf("kigcard", "png", "jpg", "jpeg", "gif", "webp", "heic", "heif")) withContext(Dispatchers.IO) { file.delete() } }
    }
    private fun provider(action: String) = model.registry.plugins.firstOrNull {
        action in it.contributions.automationActions && model.registry.available(it) && (action != "sharing/publish-v1" || setup.enabled)
    }
    fun shareDraft(draft: com.kigtts.next.state.PhraseDraft) = prepare {
        val file = model.quickTextBackup.exportDraft(draft)
        try { val title = draft.title.ifBlank { draft.text.take(32) }.ifBlank { "快捷文本" }
            publishOrShare("quick-text", title, listOf(file), "application/zip", "$title.kigcard")
        } finally { withContext(Dispatchers.IO) { file.delete() } }
    }
    fun shareQuickText(key: String, group: Boolean) = prepare {
        val title = if (group) key else model.quickTexts.phrases.value.firstOrNull { it.id == key }?.let { it.title.ifBlank { it.text.take(32) } }
        requireNotNull(title) { "这条快捷文本已移除" }
        val id = if (group) requireNotNull(model.quickTexts.categoryIds.value[key]) else key
        val file = model.quickTextBackup.prepareExport(if (group) listOf(id) else emptyList(), if (group) emptyList() else listOf(id))
        try { publishOrShare("quick-text", title, listOf(file), "application/zip", "$title.kigcard") }
        finally { withContext(Dispatchers.IO) { file.delete() } }
    }
    fun shareMedia(ids: List<String>) = prepare {
        val personal = model.identities.current?.takeIf { ids.singleOrNull() == it.designId }
        val root = File(context.cacheDir, "cards/sharing/${UUID.randomUUID()}")
        try {
            if (personal != null) {
                val pdf = withContext(Dispatchers.Default) { ProfilePdfExport.write(personal, model.stickers, root) }
                val files = if (provider("sharing/publish-v1") != null) withContext(Dispatchers.Default) {
                    com.kigtts.next.identity.profileSharingFiles(personal, model.stickers, root, pdf)
                } else listOf(pdf)
                if (!publish("profile", personal.nickname.ifBlank { "个人主页" }, files)) {
                    val intent = withContext(Dispatchers.IO) { shareFileIntent(context, pdf, "个人主页.pdf", "application/pdf") }
                    context.startActivity(intent)
                }
            } else {
                val assets = withContext(Dispatchers.IO) { FacePhotoRepository(model.cards, model.identities).prepare(ids, false, true, root)
                    .filterNot { it.format == "PROFILE" } }
                val files = assets.map { requireNotNull(it.file) }
                if (!publish("photos", "照片", files)) {
                    val intent = withContext(Dispatchers.IO) { faceSystemShareIntent(context, assets, false) }
                    context.startActivity(intent)
                }
            }
        } finally { withContext(Dispatchers.IO) { root.deleteRecursively() } }
    }
    private fun prepare(work: suspend () -> Unit) {
        if (preparing?.isActive == true) return
        preparing = scope.launch {
            try { setup.ensure(); work() }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { model.message(error.message ?: "暂时无法分享，请重试") }
        }
    }
    private suspend fun publishOrShare(kind: String, title: String, files: List<File>, mime: String, name: String) {
        if (publish(kind, title, files)) return
        val intent = withContext(Dispatchers.IO) { shareFileIntent(context, files.single(), name, mime) }
        context.startActivity(intent)
    }
    private suspend fun publish(kind: String, title: String, files: List<File>): Boolean {
        val provider = provider("sharing/publish-v1") ?: return false
        val folder = "outgoing/${UUID.randomUUID()}"
        val storage = model.pluginManager.paths.storage(provider.descriptor.id)
        withContext(Dispatchers.IO) {
            val root = storage.file(folder).apply { mkdirs() }
            try {
                val resources = files.mapIndexed { index, source ->
                    val name = "${index}.${source.extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,12}")) } ?: "bin"}"
                    source.copyTo(File(root, name))
                    JSONObject().put("path", name).put("name", source.name).put("bytes", source.length())
                }
                File(root, "share.json").writeText(JSONObject().put("schema", 1).put("kind", kind).put("title", title.take(160))
                    .put("files", JSONArray(resources)).toString(), Charsets.UTF_8)
            } catch (error: Exception) { root.deleteRecursively(); throw error }
        }
        model.invoke(provider.descriptor.id, "sharing/publish-v1", mapOf("resourcePath" to "$folder/share.json"))
        return true
    }
}
