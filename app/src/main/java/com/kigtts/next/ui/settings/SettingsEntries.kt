package com.kigtts.next.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.kigtts.next.state.Destination
import com.kigtts.next.state.SettingsCategory
import com.kigtts.next.ui.components.vector
import com.kigtts.plugin.api.PluginContext
import com.kigtts.plugin.api.PluginRegistry
import com.kigtts.plugin.api.SettingsEntry

internal data class SettingsLink(val id: String, val title: String, val summary: String,
    val icon: ImageVector?, val destination: Destination)
internal data class SettingsCard(val id: String, val title: String, val subtitle: String,
    val links: List<SettingsLink>)

/** Formal entries share native cards. Old plugins retain their page entrances until rebuilt. */
internal fun settingsCards(registry: PluginRegistry?, context: PluginContext): List<SettingsCard> {
    val reserved = linkedMapOf(
        "appearance" to ("外观与交互" to "调整显示效果和操作反馈"),
        "audio" to ("音频" to ""),
        "content" to ("内容" to ""),
        "extensions" to ("功能与扩展" to ""),
    )
    fun category(id: String, category: SettingsCategory, icon: ImageVector) =
        SettingsLink(id, category.title, category.summary, icon, Destination.SettingsDetail(category))
    val native = mapOf(
        "appearance" to listOf(category("appearance", SettingsCategory.APPEARANCE, Icons.Default.Palette),
            category("feedback", SettingsCategory.FEEDBACK, Icons.Default.Vibration)),
        "content" to listOf(category("web", SettingsCategory.WEB, Icons.Default.Language)),
        "extensions" to listOf(category("experimental", SettingsCategory.EXPERIMENTAL, Icons.Default.Science),
            SettingsLink("plugins", "插件管理", "管理插件", Icons.Default.Extension, Destination.Plugins)),
    )
    data class Feature(val pluginId: String, val entry: SettingsEntry)
    val features = registry?.plugins.orEmpty().flatMap { plugin ->
        val contributions = plugin.contributions(context)
        val formal = contributions.settingsEntries
        val formalPages = formal.map { it.pageId }.toSet()
        val legacy = contributions.drawerEntries.filter { it.pageId !in formalPages }.distinctBy { it.pageId }
            .map { SettingsEntry(it.id, it.label, it.pageId, it.icon) }
        (formal + legacy).map { Feature(plugin.descriptor.id, it) }
    }.sortedWith(compareBy({ it.entry.order }, { it.pluginId }, { it.entry.id }))
    val ids = reserved.keys + features.map { it.entry.cardId }.distinct().filterNot(reserved::containsKey)
    return ids.mapNotNull { id ->
        val entries = features.filter { it.entry.cardId == id }
        val links = native[id].orEmpty() + entries.map { (pluginId, entry) ->
            SettingsLink("plugin/$pluginId/${entry.id}", entry.label, entry.summary, if (entry.icon == com.kigtts.plugin.api.PluginIcon.NONE) null else entry.icon.vector(),
                Destination.PluginPage(pluginId, entry.pageId))
        }
        if (links.isEmpty()) null else {
            val heading = reserved[id] ?: (entries.first().entry.cardTitle.ifBlank { "扩展" } to "")
            SettingsCard(id, heading.first, heading.second, links)
        }
    }
}
