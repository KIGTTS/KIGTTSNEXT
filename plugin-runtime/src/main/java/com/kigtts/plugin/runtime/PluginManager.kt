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
class PluginManager(private val context: Context, private val builtins: List<KigPlugin>) {
    val paths = PluginPaths(context)
    val packages = PluginPackageStore(paths)
    val registry = PluginRegistry(builtins)
    private val readiness = TtsReadiness(registry)
    @Volatile var quickTextBrokerFactory:((String,Set<String>)->QuickTextBroker)?=null
    private val mutation = Mutex()
    private val loaded = linkedMapOf<String, Pair<Int, RemotePlugin>>()
    @Volatile var status: List<PluginStatus> = emptyList()
        private set
    suspend fun reload() = mutation.withLock { reloadLocked() }
    suspend fun workers() = mutation.withLock { loaded.map { (id, entry) -> WorkerStatus(id, entry.first, entry.second.alive) } }
    suspend fun restart(id: String) = mutation.withLock { paths.validateId(id);unload(id);reloadLocked() }
    suspend fun refreshContext(context: PluginContext): Boolean = mutation.withLock {
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
    private suspend fun reloadLocked() {
        val installed = withContext(Dispatchers.IO) { packages.installed() }
        loaded.filterValues { !it.second.alive }.keys.toList().forEach { unload(it) }
        loaded.keys.filter { id -> installed.none { it.manifest.id == id && it.enabled } }.toList().forEach { unload(it) }
        val statuses = installed.map { info ->
            var error: String? = null
            if (info.enabled && info.manifest.id !in loaded) {
                val slot = workerServices.indices.firstOrNull { candidate -> loaded.values.none { it.first == candidate } }
                if (slot == null) error = "当前最多同时启用 ${workerServices.size} 个外部插件"
                else {
                    try {
                        require(builtins.none { it.descriptor.id == info.manifest.id }) { "插件 ID 与内置界面插件冲突" }
                        val connection = WorkerConnection(context, slot,
                            if (info.manifest.capabilities.any { it in setOf("speech-service", "asr-service") })
                                HostSpeechBroker(info.manifest.id, registry, paths, "speech-service" in info.manifest.capabilities, "asr-service" in info.manifest.capabilities) else null,
                            if(info.manifest.capabilities.any {it in setOf("quick-text-read","quick-text-write")})
                                quickTextBrokerFactory?.invoke(info.manifest.id,info.manifest.capabilities.toSet())?.let(::HostQuickTextBroker) else null)
                        val proxy = RemotePlugin(connection, connection.connect(info.manifest.id))
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
        registry.replace(builtins + loaded.values.map { it.second })
    }
    private suspend fun unload(id: String) {
        val item = loaded[id] ?: return
        ProviderCatalog(paths).save(item.second)
        item.second.close()
        loaded.remove(id)
        registry.replace(builtins + loaded.values.map { it.second })
    }
    suspend fun install(file: File, publisher: String) = mutation.withLock {
        val id = withContext(Dispatchers.IO) { packages.inspect(file).manifest.id }
        unload(id)
        try { withContext(Dispatchers.IO) { packages.install(file, publisher) } }
        finally { reloadLocked() }
    }
    suspend fun enable(id: String, enabled: Boolean) = mutation.withLock {
        if (!enabled) unload(id)
        withContext(Dispatchers.IO) { packages.enabled(id, enabled) }
        reloadLocked()
    }
    suspend fun uninstall(id: String) = mutation.withLock {
        unload(id)
        withContext(Dispatchers.IO) { packages.uninstall(id) }
        reloadLocked()
    }
    suspend fun importResource(id: String, directory: String, name: String, zip: Boolean,
        open: () -> java.io.InputStream): String = mutation.withLock {
        unload(id)
        try { withContext(Dispatchers.IO) { ResourceImporter.import(paths.storage(id), open(), directory, name, zip) } }
        finally { reloadLocked() }
    }
    suspend fun close() = mutation.withLock { readiness.close(); loaded.keys.toList().forEach { unload(it) } }
}
