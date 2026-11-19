// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.*

/** A settings detail page: identity header, nested information/settings cards, then voice choices. */
internal fun voicePackDetails(id: String, entry: PackEntry, settings: ServiceSettings, library: VoiceLibrary, locale: String): PluginPage {
    fun t(key: String) = ReadingCopy.text(key, locale)
    val pack = entry.pack; val owner = entry.provider.pluginId; val available = entry.provider.enabled
    val name = if (owner == SYSTEM_PROVIDER) t("system_default") else pack.metadata.name
    val remark = if (owner == SYSTEM_PROVIDER) t("system_synthesis") else pack.metadata.remark
    val providerName = if (owner == SYSTEM_PROVIDER) t("system") else entry.provider.displayName
    val voices = entry.provider.info.voices.filter { it.id in pack.voiceIds }.distinctBy { it.id }
    val languages = voices.map { it.language }.filter(String::isNotBlank).distinct()
    val blocks = buildList {
        if (!available) add(PageBlock.Notice(t("pack_disabled_help")))
        add(PageBlock.Group(t("pack_information"), buildList {
            add(PageBlock.Text(t("pack_engine") + "\n" + providerName))
            if (pack.providerLabel.isNotBlank()) add(PageBlock.Text(t("pack_provider") + "\n" + pack.providerLabel))
            add(PageBlock.Text(t("pack_voice_count") + "\n" + pack.voiceIds.distinct().size))
            if (languages.isNotEmpty()) add(PageBlock.Text(t("pack_language") + "\n" + languages.joinToString(" / ") { languageName(it, locale).ifBlank { it } }))
            if (remark.isNotBlank() && !pack.editable) add(PageBlock.Text(t("pack_remark") + "\n" + remark))
        }))
        if (available) {
            add(PageBlock.Group(t("pack_settings"), buildList {
                add(PageBlock.Input(t("pack_use"), "", packAction("pack-use", entry), icon = PluginIcon.SPEAKER))
                add(PageBlock.Toggle(t("pin"), library.pinned(entry.key, pack.metadata.pinned), packAction("pack-pin", entry), icon = PluginIcon.PIN))
                if (pack.editable) {
                    add(PageBlock.Input(t("pack_name"), pack.metadata.name, packAction("pack-name", entry)))
                    add(PageBlock.Input(t("pack_remark"), pack.metadata.remark, packAction("pack-remark", entry), multiline = true, icon = PluginIcon.TEXT))
                    add(PageBlock.Input(t("pack_change_avatar"), "", packAction("pack-avatar", entry), icon = PluginIcon.IMAGE))
                }
            }))
            if (pack.parameters.isNotEmpty()) add(PageBlock.Group(t("audio_settings"),
                packSettingsControls(entry).controls.filterIsInstance<PluginControl.Slider>().map {
                    PageBlock.Slider(it.copy(actionId = it.actionId.replaceFirst("pack-setting/", "pack-setting-inline/")))
                } +
                    PageBlock.Input(t("pack_reset"), "", packAction("pack-settings-inline-reset", entry), icon = PluginIcon.SETTINGS)))
            val files = buildList {
                if (entry.provider.info.importsVoicePacks) add(PageBlock.Input(t("pack_update"), "", packAction("pack-update", entry), icon = PluginIcon.ADD))
                if (pack.exportable) add(PageBlock.Input(t("export_pack"), "", packAction("pack-export", entry), icon = PluginIcon.EXPORT))
                if (pack.deletable) add(PageBlock.Input(t("delete_pack"), "", packAction("pack-delete", entry), symbol = "delete"))
            }
            if (files.isNotEmpty()) add(PageBlock.Group(t("pack_management"), files))
        }
    }
    val choices = if (owner == SYSTEM_PROVIDER || voices.isEmpty()) null else ManagementList("pack-voices", voices.map { voice ->
        val action = "voice/${encodeId(owner)}/${encodeId(voice.id)}"
        ManagementItem(voice.id, voice.name, languageName(voice.language, locale), PluginIcon.SPEAKER, group = t("voice"),
            selected = settings.provider == owner && settings.voice == voice.id,
            primaryAction = action.takeIf { available }, selection = ManagementSelection(action, available))
    })
    return PluginPage(id, name, blocks, choices,
        header = ManagementItem("voice-pack-header", name,
            remark.ifBlank { pack.providerLabel.ifBlank { providerName } }, PluginIcon.SPEAKER,
            image = pack.avatarPath?.let { PluginResource(it, owner) }))
}
