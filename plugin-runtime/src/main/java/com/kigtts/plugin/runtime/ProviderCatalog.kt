package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*

/** Cache protocol metadata so disabled engines keep their packs visible without executing code. */
internal class ProviderCatalog(private val paths: PluginPaths) {
    fun save(plugin: KigPlugin) {
        runCatching {
        val info = plugin.tts?.info?.takeIf { it.role == TtsRole.PROVIDER } ?: return
        val file = paths.storage(plugin.descriptor.id).file("config/host/tts-catalog.json").apply { parentFile!!.mkdirs() }
        file.writeText(PluginJson.encodeToString(TtsProvider(plugin.descriptor.id, plugin.descriptor.name, info)))
        }
    }
    fun disabled(active: Set<String>): List<TtsProvider> = PluginPackageStore(paths).installed().filter { !it.enabled && it.manifest.id !in active }.mapNotNull { installed ->
        runCatching {
            PluginJson.decodeFromString<TtsProvider>(paths.storage(installed.manifest.id).file("config/host/tts-catalog.json").readText())
                .takeIf { it.pluginId == installed.manifest.id && it.info.role == TtsRole.PROVIDER }?.copy(enabled = false)
        }.getOrNull()
    }
}
