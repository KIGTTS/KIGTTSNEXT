// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.*
import java.util.Base64

internal fun encodeId(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
internal fun decodeId(value: String) = String(Base64.getUrlDecoder().decode(value))
internal fun packAction(verb: String, entry: PackEntry) = "$verb/${encodeId(entry.provider.pluginId)}/${encodeId(entry.pack.id)}"

internal class VoiceLibraryPages(private val settings: ServiceSettings, private val library: VoiceLibrary, private val providers: () -> List<TtsProvider>) {
    fun entries() = library.ordered(voicePacks(providers()))
    fun visible() = entries().filter { library.filter == "all" || it.provider.pluginId == library.filter }
    fun page(id: String, locale: String = "zh-CN"): PluginPage? {
        fun t(key: String) = ReadingCopy.text(key, locale)
        if (id == "packs") {
            val sources = providers()
            val filters = listOf(ManagementFilter("all", t("all"), "pack-filter/${encodeId("all")}", library.filter == "all")) + sources.map {
                ManagementFilter(it.pluginId, if (it.pluginId == SYSTEM_PROVIDER) t("system") else it.displayName,
                    "pack-filter/${encodeId(it.pluginId)}", library.filter == it.pluginId)
            }
            return PluginPage(id, t("manage_packs"), listOf(PageBlock.Actions(listOf(
                PageAction("import-voice-package", t("import_pack"), PluginIcon.ADD)))),
                ManagementList("voice-packs", visible().map { entry ->
                    val pack = entry.pack; val pinned = library.pinned(entry.key, pack.metadata.pinned)
                    val actions = mutableListOf(PageAction(packAction("pack-pin", entry), if (pinned) t("unpin") else t("pin"), PluginIcon.PIN),
                        PageAction(packAction("pack", entry), t("details"), PluginIcon.INFO))
                    if (pack.parameters.isNotEmpty() && entry.provider.enabled)
                        actions += PageAction(packAction("pack-settings", entry), t("audio_settings"), PluginIcon.SETTINGS)
                    if (pack.exportable && entry.provider.enabled) actions += PageAction(packAction("pack-export", entry), t("export_pack"), PluginIcon.EXPORT)
                    if (pack.deletable && entry.provider.enabled) actions += PageAction(packAction("pack-delete", entry), t("delete_pack"), PluginIcon.CLOSE, symbol = "delete")
                    ManagementItem(entry.key, if (entry.provider.pluginId == SYSTEM_PROVIDER) t("system_default") else pack.metadata.name,
                        if (entry.provider.pluginId == SYSTEM_PROVIDER) t("system_synthesis") else listOfNotNull(
                            pack.metadata.remark.takeIf { it.isNotBlank() }, t("engine_disabled").takeUnless { entry.provider.enabled }).joinToString(" · "), PluginIcon.SPEAKER,
                        pack.avatarPath?.let { PluginResource(it, entry.provider.pluginId) },
                        selected = if (entry.provider.pluginId == SYSTEM_PROVIDER) settings.provider == null
                            else settings.provider == entry.provider.pluginId && settings.voice in pack.voiceIds,
                        pinned = pinned, group = if (pinned) "pinned" else "normal", primaryAction = if (entry.provider.enabled) packAction("pack-use", entry) else packAction("pack", entry), actions = actions,
                        badges = listOf(if (entry.provider.pluginId == SYSTEM_PROVIDER) t("system") else pack.providerLabel.ifBlank { entry.provider.displayName.take(32) }),
                        selection = ManagementSelection(packAction("pack-use", entry), entry.provider.enabled))
                }, "pack-order", t("no_packs"), filters))
        }
        if (!id.startsWith("pack/")) return null
        val parts = id.split('/'); val owner = decodeId(parts[1]); val key = decodeId(parts[2])
        val entry = entries().firstOrNull { it.provider.pluginId == owner && it.pack.id == key }
            ?: return PluginPage(id, t("pack_unavailable"), listOf(PageBlock.Notice(t("pack_unavailable_help"))))
        return voicePackDetails(id, entry, settings, library, locale)
    }
}
