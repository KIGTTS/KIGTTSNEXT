package com.kigtts.plugin.runtime

import android.content.Context
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class PluginStatus(val installed: InstalledPlugin, val error: String? = null)
data class WorkerStatus(val id: String, val slot: Int, val alive: Boolean)

/** Serialized mutations prevent updates/imports/uninstall racing sessions and class loaders. */
class PluginManager(private val context: Context, private val builtins: List<KigPlugin>,
    private val pausedPackages: Set<String> = emptySet()) {
    fun isPackagePaused(id: String): Boolean = id in pausedPackages
    val builtinIds: Set<String> = builtins.map { it.descriptor.id }.toSet()
    val applicationContext: Context get() = context.applicationContext
    @Volatile var onNotificationsUnavailable: (String) -> Unit = {}
    val paths = PluginPaths(context)
    val packages = PluginPackageStore(paths)
    val registry = PluginRegistry(builtins)
    val permissions = PluginPermissions(context)
    @Volatile var automaticPermission: (String, String) -> Boolean = { _, _ -> false }
    @Volatile var safeMode: () -> Boolean = { false }
    fun permissionOwner(id: String, project: String? = null): String {
        val publisher = status.firstOrNull { it.installed.manifest.id == id }?.installed?.publisherSha256.orEmpty()
        return "${id}:$publisher" + (project?.let { ":$it" } ?: "")
    }
    suspend fun requestPermission(id: String, permission: String): Boolean {
        val installed = status.firstOrNull { it.installed.manifest.id == id }?.installed
            ?: withContext(Dispatchers.IO) { packages.installed().firstOrNull { it.manifest.id == id } } ?: return false
        if (permission !in installed.manifest.capabilities || permission !in PluginPermissions.sensitive) return false
        if (permission !in setOf("notifications", "overlay") && automaticPermission(id, permission)) return true
        return permissions.request("${id}:${installed.publisherSha256}", permission, installed.manifest.name,
            installed.manifest.permissionPurposes[permission] ?: "插件未提供具体用途；此权限允许${PluginPermissions.label(permission)}。")
    }
    private val readiness = TtsReadiness(registry)
    @Volatile var quickTextBrokerFactory:((String,Set<String>)->QuickTextBroker)?=null
    @Volatile var appBrokerFactory: ((String, Set<String>) -> AppBroker)? = null
    @Volatile var stickerBrokerFactory: ((String, Set<String>) -> StickerBroker)? = null
    private val uiInvalidations = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val uiChanges: kotlinx.coroutines.flow.SharedFlow<String> = uiInvalidations
    private val mutation = Mutex()
    private val loaded = linkedMapOf<String, Pair<Int, RemotePlugin>>()
    private val builtinBrokers = mutableListOf<HostSpeechBroker>()
    private var builtinsAttached = false
    @Volatile var status: List<PluginStatus> = emptyList()
        private set
    @Volatile var installedCatalogReady: Boolean = false
        private set
    suspend fun reload() = mutation.withLock { reloadLocked() }
    suspend fun workers() = mutation.withLock { loaded.map { (id, entry) -> WorkerStatus(id, entry.first, entry.second.alive) } }
    suspend fun restart(id: String) = mutation.withLock { paths.validateId(id);unload(id);reloadLocked() }
    suspend fun refreshContext(context: PluginContext): Boolean = mutation.withLock {
        if (builtinsAttached) builtins.forEach { it.contributions(context) }
        readiness.observe(context)
        var changed = false
        for ((_, proxy) in loaded.values) {
            if (proxy.alive) try { if (proxy.refresh(context)) changed = true }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { }
        }
        if (changed) registry.replace(builtins + loaded.values.map { it.second })
        changed
    }
    suspend fun refreshUi(id: String, context: PluginContext): Boolean = mutation.withLock {
        val proxy = loaded[id]?.second ?: return@withLock false
        if (!proxy.alive) return@withLock false
        val changed = proxy.refresh(context)
        if (changed) registry.replace(builtins + loaded.values.map { it.second })
        changed
    }
    private suspend fun reloadLocked() {
        if (!builtinsAttached) {
            for (plugin in builtins) {
                val broker = HostSpeechBroker(plugin.descriptor.id, registry, paths)
                try {
                    withContext(Dispatchers.IO) { plugin.attach(ScopedEnvironment(context, paths.storage(plugin.descriptor.id),
                        File(context.filesDir, "builtin-assets/${plugin.descriptor.id}").apply { mkdirs() }, false,
                        speechBroker = BuiltinSpeechBroker(plugin.descriptor.id, registry, broker),
                        recognitionBroker = broker.builtinRecognition())) }
                    builtinBrokers += broker
                } catch (error: Exception) { broker.close(); throw error }
            }
            builtinsAttached = true
        }
        // A previous standalone build may still be installed. Keep its data and package,
        // but never start a second service or expose it as an independently managed plugin.
        val installed = withContext(Dispatchers.IO) {
            packages.installed().filterNot { it.manifest.id in builtinIds || isPackagePaused(it.manifest.id) }
        }
        loaded.filterValues { !it.second.alive }.keys.toList().forEach { unload(it) }
        loaded.keys.filter { id -> safeMode() || installed.none { it.manifest.id == id && it.enabled } }.toList().forEach { unload(it) }
        val statuses = installed.map { info ->
            var error: String? = null
            if (safeMode() && info.enabled) error = "安全模式已临时停用"
            if (!safeMode() && info.enabled && info.manifest.id !in loaded) {
                val slot = workerServices.indices.firstOrNull { candidate -> loaded.values.none { it.first == candidate } }
                if (slot == null) error = "当前最多同时启用 ${workerServices.size} 个外部插件"
                else {
                    try {
                        require(builtins.none { it.descriptor.id == info.manifest.id }) { "插件 ID 与内置界面插件冲突" }
                        val connection = WorkerConnection(context, slot,
                            if (info.manifest.capabilities.any { it in setOf("speech-service", "asr-service") })
                                HostSpeechBroker(info.manifest.id, registry, paths, "speech-service" in info.manifest.capabilities, "asr-service" in info.manifest.capabilities) else null,
                            if(info.manifest.capabilities.any {it in setOf("quick-text-read","quick-text-write")})
                                quickTextBrokerFactory?.invoke(info.manifest.id,info.manifest.capabilities.toSet())?.let { backend -> HostQuickTextBroker(object : QuickTextBroker {
                                    override suspend fun request(command: QuickTextCommand): QuickTextReply = if (requestPermission(info.manifest.id, quickTextPermission(command, info.manifest.capabilities.toSet()))) backend.request(command)
                                        else QuickTextReply.Failure(QuickTextError.PERMISSION_DENIED, "快捷文本权限尚未授权或已禁用")
                                    override fun changes(afterRevision: Long) = kotlinx.coroutines.flow.flow { if (requestPermission(info.manifest.id, if ("quick-text-read" in info.manifest.capabilities) "quick-text-read" else "quick-text-write")) backend.changes(afterRevision).collect { emit(it) } }
                                }) } else null,
                            appBrokerFactory?.invoke(info.manifest.id, info.manifest.capabilities.toSet())?.let(::HostAppBroker),
                            if (info.manifest.capabilities.any { it in setOf("stickers-read", "stickers-write") }) stickerBrokerFactory?.invoke(info.manifest.id, info.manifest.capabilities.toSet())?.let { backend -> HostStickerBroker(object : StickerBroker {
                                override suspend fun request(command: StickerCommand): StickerReply = if (requestPermission(info.manifest.id, stickerPermission(command, info.manifest.capabilities.toSet()))) backend.request(command)
                                    else StickerReply.Failure(StickerError.PERMISSION_DENIED, "图库权限尚未授权或已禁用")
                                override fun changes(afterRevision: Long) = kotlinx.coroutines.flow.flow { if (requestPermission(info.manifest.id, if ("stickers-read" in info.manifest.capabilities) "stickers-read" else "stickers-write")) backend.changes(afterRevision).collect { emit(it) } }
                            }) } else null,
                            { uiInvalidations.tryEmit(info.manifest.id) })
                        val proxy = RemotePlugin(connection, connection.connect(info.manifest.id)) { permission -> requestPermission(info.manifest.id, permission) }
                        try { PluginRegistry(builtins + loaded.values.map { it.second } + proxy) }
                        catch (failure: Exception) { proxy.close(); throw failure }
                        loaded[info.manifest.id] = slot to proxy
                        ProviderCatalog(paths).save(proxy)
                    } catch (failure: Exception) { error = failure.message ?: "插件启动失败" }
                }
            }
            PluginStatus(info, error)
        }
        status = statuses
        installedCatalogReady = true
        registry.replace(builtins + loaded.values.map { it.second })
    }
    private suspend fun unload(id: String) {
        onNotificationsUnavailable(id)
        val item = loaded[id] ?: return
        ProviderCatalog(paths).save(item.second)
        item.second.close()
        loaded.remove(id)
        registry.replace(builtins + loaded.values.map { it.second })
    }
    suspend fun install(file: File, publisher: String, allowVersionChange: Boolean = false) = mutation.withLock {
        val id = withContext(Dispatchers.IO) { packages.inspect(file).manifest.id }
        require(!isPackagePaused(id)) { "此插件暂不可用" }
        require(id !in builtinIds) { "此功能已内置，请在软件设置中使用" }
        unload(id)
        try { withContext(Dispatchers.IO) { packages.install(file, publisher, allowVersionChange) } }
        finally { reloadLocked() }
    }
    suspend fun enable(id: String, enabled: Boolean) = mutation.withLock {
        require(!enabled || !isPackagePaused(id)) { "此插件暂不可用" }
        if (!enabled) unload(id)
        withContext(Dispatchers.IO) { packages.enabled(id, enabled) }
        reloadLocked()
    }
    suspend fun uninstall(id: String) = mutation.withLock {
        permissions.clear(permissionOwner(id))
        unload(id)
        withContext(Dispatchers.IO) { packages.uninstall(id) }
        reloadLocked()
    }
    suspend fun importResource(id: String, directory: String, name: String, zip: Boolean,
        open: () -> java.io.InputStream): String = mutation.withLock {
        // User-picked attachments are staged atomically in the caller's inbox. Keep live
        // chat workers and queued conversations attached while copying new inputs.
        if (directory == "imports" || directory.startsWith("imports/"))
            return@withLock withContext(Dispatchers.IO) { ResourceImporter.import(paths.storage(id), open(), directory, name, zip) }
        unload(id)
        try { withContext(Dispatchers.IO) { ResourceImporter.import(paths.storage(id), open(), directory, name, zip) } }
        finally { reloadLocked() }
    }
    suspend fun close() = mutation.withLock {
        readiness.close(); loaded.keys.toList().forEach { unload(it) }
        if (builtinsAttached) builtins.forEach { it.close() }
        builtinBrokers.forEach { it.close() }; builtinBrokers.clear(); builtinsAttached = false
    }
}
