package com.kigtts.plugin.runtime

import android.util.AtomicFile
import com.kigtts.plugin.api.*
import java.io.File

/** Cache protocol metadata so disabled engines keep their packs visible without executing code. */
internal class ProviderCatalog(private val paths: PluginPaths) {
    fun save(plugin: KigPlugin) {
        runCatching {
        plugin.tts?.info?.takeIf { it.role == TtsRole.PROVIDER }?.let { info ->
            val id = plugin.descriptor.id
            val file = paths.storage(id).file("config/host/tts-catalog.json").apply { parentFile!!.mkdirs() }
            val provider = TtsProvider(id, plugin.descriptor.name, info, settingsPages = plugin.engineSettingsPages())
            val previous = cachedTts(id)
            if (provider != previous && (info.availability == EngineAvailability.READY || previous == null)) {
                val atomic = AtomicFile(file)
                val output = atomic.startWrite()
                try {
                    output.write(PluginJson.encodeToString(provider).encodeToByteArray())
                    atomic.finishWrite(output)
                } catch (error: Exception) {
                    atomic.failWrite(output)
                    throw error
                }
            }
        }
        plugin.asr?.info?.takeIf { it.role == AsrRole.PROVIDER }?.let { info ->
            val file = paths.storage(plugin.descriptor.id).file("config/host/asr-catalog.json").apply { parentFile!!.mkdirs() }
            file.writeText(PluginJson.encodeToString(AsrProvider(plugin.descriptor.id, plugin.descriptor.name, info,
                settingsPages = plugin.engineSettingsPages())))
        }
        }
    }
    fun cachedTts(id: String): TtsProvider? = runCatching {
        val pointer = File(paths.packageDirectory(id), "current")
        val file = paths.existingResource(id, "config/host/tts-catalog.json") ?: return null
        // The snapshot belongs to the installed package version, including after upgrades or rollbacks.
        if (!pointer.isFile || file.lastModified() < pointer.lastModified()) return null
        PluginJson.decodeFromString<TtsProvider>(AtomicFile(file).readFully().decodeToString())
            .takeIf { it.pluginId == id && it.info.role == TtsRole.PROVIDER }
    }.getOrNull()

    fun disabled(active: Set<String>): List<TtsProvider> = PluginPackageStore(paths).installed().filter { !it.enabled && it.manifest.id !in active }.mapNotNull { installed ->
        cachedTts(installed.manifest.id)?.copy(enabled = false)
    }
    fun disabledAsr(active: Set<String>): List<AsrProvider> = PluginPackageStore(paths).installed().filter {
        it.manifest.id !in active && "asr-service" !in it.manifest.capabilities &&
            it.manifest.capabilities.any { capability -> capability in setOf("asr-batch", "asr-segmented", "asr-incremental") }
    }.map { installed ->
        runCatching {
            PluginJson.decodeFromString<AsrProvider>(paths.storage(installed.manifest.id).file("config/host/asr-catalog.json").readText())
                .takeIf { it.pluginId == installed.manifest.id && it.info.role == AsrRole.PROVIDER }?.copy(enabled = false)
        }.getOrNull() ?: AsrProvider(installed.manifest.id, installed.manifest.name, AsrInfo(
            batch = "asr-batch" in installed.manifest.capabilities,
            streaming = when { "asr-incremental" in installed.manifest.capabilities -> StreamingKind.INCREMENTAL
                "asr-segmented" in installed.manifest.capabilities -> StreamingKind.SEGMENTED; else -> StreamingKind.NONE },
            availability = EngineAvailability.NEEDS_RESOURCES), enabled = false)
    }
}
