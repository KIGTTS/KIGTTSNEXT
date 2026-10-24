package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*

/** Cache protocol metadata so disabled engines keep their packs visible without executing code. */
internal class ProviderCatalog(private val paths: PluginPaths) {
    fun save(plugin: KigPlugin) {
        runCatching {
        plugin.tts?.info?.takeIf { it.role == TtsRole.PROVIDER }?.let { info ->
            val file = paths.storage(plugin.descriptor.id).file("config/host/tts-catalog.json").apply { parentFile!!.mkdirs() }
            file.writeText(PluginJson.encodeToString(TtsProvider(plugin.descriptor.id, plugin.descriptor.name, info, settingsPages = plugin.engineSettingsPages())))
        }
        plugin.asr?.info?.takeIf { it.role == AsrRole.PROVIDER }?.let { info ->
            val file = paths.storage(plugin.descriptor.id).file("config/host/asr-catalog.json").apply { parentFile!!.mkdirs() }
            file.writeText(PluginJson.encodeToString(AsrProvider(plugin.descriptor.id, plugin.descriptor.name, info,
                settingsPages = plugin.engineSettingsPages())))
        }
        }
    }
    fun disabled(active: Set<String>): List<TtsProvider> = PluginPackageStore(paths).installed().filter { !it.enabled && it.manifest.id !in active }.mapNotNull { installed ->
        runCatching {
            PluginJson.decodeFromString<TtsProvider>(paths.storage(installed.manifest.id).file("config/host/tts-catalog.json").readText())
                .takeIf { it.pluginId == installed.manifest.id && it.info.role == TtsRole.PROVIDER }?.copy(enabled = false)
        }.getOrNull()
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
