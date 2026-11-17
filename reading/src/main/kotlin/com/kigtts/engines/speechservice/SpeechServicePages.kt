// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.*
import java.util.Locale

/** Configuration and voice selection use the host's grouped settings renderer. */
internal class SpeechServicePages(private val settings: ServiceSettings, private val cacheBytes: () -> Long = { 0L },
    private val providers: () -> List<TtsProvider>) {
    fun page(id: String, active: Boolean, locale: String = "zh-CN"): PluginPage? {
        fun t(key: String) = ReadingCopy.text(key, locale)
        if (id == "manager") {
            val catalog = providers()
            val source = catalog.firstOrNull { it.pluginId == settings.provider && it.enabled && it.info.availability == EngineAvailability.READY }
            val voice = source?.info?.voices?.firstOrNull { it.id == (settings.voice ?: source.info.selectedVoiceId) }
                ?: source?.info?.voices?.firstOrNull()
            val pack = source?.info?.packs?.firstOrNull { voice?.id in it.voiceIds }
            val actions = if (active) emptyList() else listOf(PageBlock.Actions(listOf(PageAction("activate", t("activate"), PluginIcon.SPEAKER))))
            return PluginPage(id, t("manager"), actions + buildList<PageBlock> {
                add(PageBlock.Group(t("read_aloud"), listOf(
                    PageBlock.Input(t("voice"), voice?.name ?: t("system_default"), "library", icon = PluginIcon.SPEAKER),
                    PageBlock.Slider(PluginControl.Slider(t("speed"), "save-rate", settings.rate, .25f, 4f, suffix = "×")))))
                if (pack != null && pack.parameters.isNotEmpty()) {
                    val controls = packSettingsControls(PackEntry(requireNotNull(source), pack)).controls.filterIsInstance<PluginControl.Slider>()
                    add(PageBlock.Group(pack.metadata.name, controls.map { PageBlock.Slider(it) }))
                }
                val entries = catalog.filter { it.enabled }.flatMap { provider -> provider.settingsPages.map { page ->
                    PageAction("settings/${encodeId(provider.pluginId)}/${encodeId(page.id)}",
                        if (provider.settingsPages.size == 1) provider.name else "${provider.name} · ${page.title}", page.icon)
                } }
                if (entries.isNotEmpty()) add(PageBlock.Group(t("engine_settings"), listOf(PageBlock.Actions(entries))))
                add(PageBlock.Group(t("audio_cache"), buildList {
                    add(PageBlock.Toggle(t("auto_cache"), settings.cacheEnabled, "cache-toggle",
                        if (source?.info?.packs?.isNotEmpty() == true && source.pluginId == "engine.cloud-tts")
                            t("cloud_cache_help")
                        else t("cache_help")))
                    if (settings.cacheEnabled) add(PageBlock.Input(t("cache_rebuild"), t("cache_rebuild_help"), "cache-rebuild", symbol = "refresh"))
                    add(PageBlock.Input(t("clear_cache"), formatCacheSize(cacheBytes()), "cache-clear", icon = PluginIcon.CLOSE))
                }))
                add(PageBlock.Group(t("loading_options"), buildList {
                    add(PageBlock.Toggle(t("keep_loaded"), settings.resident, "resident", t("keep_loaded_help")))
                    if (!settings.resident) add(PageBlock.Slider(PluginControl.Slider(t("idle_time"), "idle-minutes",
                        settings.idleMinutes.toFloat(), 1f, 15f, steps = 13, suffix = t("minutes"))))
                }))
            })
        }
        if (id != "voices" && !id.startsWith("voices/")) return null
        val sources = providers().filter { it.enabled && it.info.availability == EngineAvailability.READY }
            .sortedBy { if (it.pluginId == settings.provider) 0 else 1 }
        val default = ManagementItem("system-default", t("system_default"), t("system_synthesis"), icon = PluginIcon.SPEAKER,
            selected = settings.provider == null, group = t("system_voices"), primaryAction = "system-default", selection = ManagementSelection("system-default"))
        val items = sources.flatMap { source ->
            if (source.pluginId == SYSTEM_PROVIDER) listOf(default) else source.info.voices.distinctBy { it.id }.map { voice ->
                ManagementItem("${source.pluginId}/${voice.id}", voice.name, languageName(voice.language, locale), PluginIcon.SPEAKER,
                    selected = settings.provider == source.pluginId && settings.voice == voice.id,
                    group = source.name,
                    primaryAction = "voice/${encodeId(source.pluginId)}/${encodeId(voice.id)}",
                    selection = ManagementSelection("voice/${encodeId(source.pluginId)}/${encodeId(voice.id)}"))
            }
        }.ifEmpty { listOf(default) }
        val page = (id.substringAfter('/', "0").toIntOrNull() ?: 0).coerceIn(0, (items.size - 1) / 24)
        val navigation = buildList {
            if (page > 0) add(PageAction("sounds/${page - 1}", t("previous")))
            if ((page + 1) * 24 < items.size) add(PageAction("sounds/${page + 1}", t("next")))
        }
        return PluginPage(id, t("choose_voice"), if (navigation.isEmpty()) emptyList() else listOf(PageBlock.Actions(navigation)),
            ManagementList("voice-picker/$page", items.drop(page * 24).take(24)))
    }
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

internal fun languageName(tag: String, displayLocale: String = "zh-CN"): String = Locale.forLanguageTag(tag).let { locale ->
    if (locale.language.isBlank()) "" else locale.getDisplayName(Locale.forLanguageTag(displayLocale))
}
