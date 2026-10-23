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
    val icon: ImageVector?, val destination: Destination, val badge: com.kigtts.plugin.api.PluginBadge? = null)
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
            category("feedback", SettingsCategory.FEEDBACK, Icons.Default.Vibration),
            category("overlay", SettingsCategory.OVERLAY, Icons.Default.BubbleChart)),
        "content" to listOf(category("web", SettingsCategory.WEB, Icons.Default.Language),
            category("camera", SettingsCategory.CAMERA, Icons.Default.PhotoCamera),
            category("backup", SettingsCategory.BACKUP_RESTORE, Icons.Default.SettingsBackupRestore)),
        "extensions" to listOf(SettingsLink("plugins", "插件管理", "管理插件", Icons.Default.Extension, Destination.Plugins),
            category("experimental", SettingsCategory.EXPERIMENTAL, Icons.Default.Science)),
    )
    data class Feature(val pluginId: String, val entry: SettingsEntry)
    val features = registry?.plugins.orEmpty().flatMap { plugin ->
        val contributions = plugin.contributions(context)
        val formal = contributions.settingsEntries
        val formalPages = formal.map { it.pageId }.toSet()
        val legacy = contributions.drawerEntries.filter { it.pageId !in formalPages }.distinctBy { it.pageId }
            .map { SettingsEntry(it.id, it.label, it.pageId, it.icon) }
        (formal + legacy).filterNot { it.cardId == "backup" }
            .mapNotNull { routedSettingsEntry(plugin, it, context) }.map { Feature(plugin.descriptor.id, it) }
    }.sortedWith(compareBy({ it.entry.order }, { it.pluginId }, { it.entry.id }))
    val ids = reserved.keys + features.map { it.entry.cardId }.distinct().filterNot(reserved::containsKey)
    return ids.mapNotNull { id ->
        val entries = features.filter { it.entry.cardId == id }
        val pluginLinks = entries.sortedBy { if (it.pluginId == "tools.package-store") 0 else 1 }.map { (pluginId, entry) ->
            SettingsLink("plugin/$pluginId/${entry.id}", entry.label, entry.summary, if (entry.icon == com.kigtts.plugin.api.PluginIcon.NONE && entry.symbol == null) null else entry.icon.vector(entry.symbol),
                Destination.PluginPage(pluginId, entry.pageId), entry.badge)
        }
        val links = if (id == "extensions") pluginLinks + native[id].orEmpty() else native[id].orEmpty() + pluginLinks
        if (links.isEmpty()) null else {
            val heading = reserved[id] ?: (entries.first().entry.cardTitle.ifBlank { "扩展" } to "")
            SettingsCard(id, heading.first, heading.second, links)
        }
    }
}

/** Compatibility plugins own their format page, while the host owns the common entrance. */
internal fun backupSettingsLinks(registry: PluginRegistry, context: PluginContext): List<SettingsLink> =
    backupSettingsCards(registry, context).flatMap { it.links }

internal fun backupSettingsCards(registry: PluginRegistry, context: PluginContext): List<SettingsCard> =
    registry.plugins.flatMap { plugin -> plugin.contributions(context).settingsEntries.filter { it.cardId == "backup" }
        .map { plugin.descriptor.id to it } }
        .sortedWith(compareBy({ it.second.order }, { it.first }, { it.second.id }))
        .groupBy { (pluginId, entry) -> pluginId to entry.cardTitle.ifBlank { "兼容格式" } }
        .map { (group, entries) -> SettingsCard("plugin/${group.first}/${group.second}", group.second, "",
            entries.map { (pluginId, entry) -> SettingsLink("plugin/$pluginId/${entry.id}", entry.label, entry.summary,
            if (entry.icon == com.kigtts.plugin.api.PluginIcon.NONE && entry.symbol == null) null else entry.icon.vector(entry.symbol),
            Destination.PluginPage(pluginId, entry.pageId), entry.badge) }) }
