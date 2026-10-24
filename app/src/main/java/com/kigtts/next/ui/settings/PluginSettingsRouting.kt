package com.kigtts.next.ui.settings

import com.kigtts.plugin.api.*

/** Old engine packages predate settings cards. Keep controls discoverable, not about pages. */
internal fun routedSettingsEntry(plugin: KigPlugin, entry: SettingsEntry, context: PluginContext): SettingsEntry? {
    if (plugin.asr?.info?.role == AsrRole.PROVIDER || plugin.tts?.info?.role == TtsRole.PROVIDER) return null
    val audio = plugin.tts != null || plugin.asr != null || plugin.voiceManager != null
    if (!audio) return entry
    val page = plugin.page(entry.pageId, context)
    if (page != null && page.management == null && page.header == null &&
        page.onEnterAction == null && page.refreshAction == null &&
        page.blocks.all { it is PageBlock.Text || it is PageBlock.Notice }) return null
    return if (entry.cardId == "extensions") entry.copy(cardId = "audio", cardTitle = "音频") else entry
}
