package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*

/** Service pages share the same formal settings entries as the main settings screen. */
internal fun KigPlugin.engineSettingsPages(): List<AsrSettingsPage> = contributions.let { contribution ->
    (contribution.settingsEntries.map { AsrSettingsPage(it.pageId, it.label, it.icon) } +
        contribution.drawerEntries.map { AsrSettingsPage(it.pageId, it.label, it.icon) }).distinctBy { it.id }
}
