package com.kigtts.next.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plugin UI entries are optional choices; discovery never adds a persistent shortcut. */
internal fun refreshOverlayPlugins(context: Context, manager: PluginManager) {
    val active = manager.registry.plugins.filter { manager.registry.available(it) && !manager.safeMode() }
    val publishers = manager.status.filter { it.installed.enabled && active.any { plugin -> plugin.descriptor.id == it.installed.manifest.id } }
        .associate { it.installed.manifest.id to it.installed.publisherSha256 }
    val entries = active.flatMap { plugin ->
        val owner = plugin.descriptor.id
        val installed = manager.status.firstOrNull { it.installed.manifest.id == owner }?.installed
        if (installed?.enabled != true) return@flatMap emptyList()
        val publisher = installed.publisherSha256
        val ui = plugin.contributions
        val pages = ui.settingsEntries.map { entry ->
            OverlayShortcut("plugin:$owner:page:${entry.id}", entry.label, entry.symbol ?: overlaySymbol(entry.icon),
                "page:${entry.pageId}", owner = owner, publisher = publisher, category = plugin.descriptor.name,
                badge = entry.badge?.toOverlayBadge())
        } + ui.drawerEntries.map { entry ->
            OverlayShortcut("plugin:$owner:drawer:${entry.id}", entry.label, overlaySymbol(entry.icon),
                "page:${entry.pageId}", owner = owner, publisher = publisher, category = plugin.descriptor.name)
        }
        val actions = ui.actions.filter { it.slot in setOf(UiSlot.HEADER_START, UiSlot.HEADER_END) }.map { entry ->
            OverlayShortcut("plugin:$owner:action:${entry.id}", entry.label, entry.symbol ?: overlaySymbol(entry.icon),
                "action", owner = owner, publisher = publisher, action = entry.id, category = plugin.descriptor.name,
                badge = entry.badge?.toOverlayBadge())
        }
        pages + actions + retainedOverlayActions(OverlayStore.get(context).settings.value.shortcuts, owner, publisher, ui)
            .filter { retained -> actions.none { it.id == retained.id } }
    }
    OverlayHub.get(context).discovered(entries, publishers)
}

/** A durable automation action can outlive its conditional toolbar button (for example AI Open). */
internal fun retainedOverlayActions(saved: List<OverlayShortcut>, owner: String, publisher: String,
    ui: PluginContributions): List<OverlayShortcut> = saved.filter {
    it.owner == owner && it.publisher == publisher && it.target == "action" && it.action in ui.automationActions &&
        it.id == "plugin:$owner:action:${it.action}"
}.map { entry ->
    entry.copy(enabled = true, badge = ui.settingsEntries.firstOrNull { it.label == entry.title }?.badge?.toOverlayBadge())
}

internal fun PluginBadge.toOverlayBadge() = OverlayBadge(if (count == null) "dot" else "number",
    when (tone) { BadgeTone.THEME -> "primary"; BadgeTone.WARNING -> "warning"; BadgeTone.CAUTION -> "caution" }, count ?: 0)

internal fun OverlayBadge.toPluginBadge(): PluginBadge? = if (kind == "number" && count <= 0) null else PluginBadge(
    count = if (kind == "number") count else null,
    tone = when (tone) { "warning" -> BadgeTone.WARNING; "caution", "yellow" -> BadgeTone.CAUTION; else -> BadgeTone.THEME })

private fun overlaySymbol(icon: PluginIcon) = when (icon) {
    PluginIcon.ROBOT2 -> "smart_toy"; PluginIcon.MIC -> "mic"; PluginIcon.SPEAKER -> "volume_up"
    PluginIcon.SETTINGS -> "settings"; PluginIcon.TEXT -> "subtitles"; PluginIcon.IMAGE -> "image"
    PluginIcon.HEARING -> "hearing"; PluginIcon.STAR -> "star"; PluginIcon.ADD -> "add"
    PluginIcon.MENU -> "menu"; PluginIcon.CLOSE -> "close"; PluginIcon.PIN -> "push_pin"
    PluginIcon.EDIT -> "edit"; PluginIcon.EXPORT -> "ios_share"; else -> "extension"
}

internal suspend fun availableOverlayApps(context: Context, entries: List<OverlayShortcut>): Set<String> = withContext(Dispatchers.IO) {
    entries.filter { it.component != null }.filter { item -> runCatching {
        val component = ComponentName.unflattenFromString(item.component!!) ?: return@runCatching false
        val info = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component), 0)?.activityInfo
        info?.enabled == true && info.exported && info.applicationInfo.enabled
    }.getOrDefault(false) }.map { it.id }.toSet()
}

internal fun resolveOverlayShortcuts(saved: List<OverlayShortcut>, candidates: List<OverlayShortcut>, apps: Set<String>): List<OverlayShortcut> =
    saved.map { item ->
        when {
            item.owner != null -> candidates.firstOrNull { it.id == item.id && it.publisher == item.publisher }
                ?.let { it.copy(showLabel = item.showLabel) } ?: item.copy(enabled = false, badge = null)
            item.component != null -> item.copy(enabled = item.id in apps)
            else -> item
        }
    }

internal fun overlayBuiltInChoices() = defaultOverlayShortcuts().map { it.copy(category = "页面") } + listOf(
    OverlayShortcut("listen", "开启或停止聆听", "hearing", "listen", category = "操作"),
    OverlayShortcut("speak", "朗读字幕", "volume_up", "speak", category = "操作"),
    OverlayShortcut("overlay-settings", "悬浮窗设置", "tune", "overlay-settings", category = "设置"),
    OverlayShortcut("settings", "软件设置", "settings", "settings", category = "设置"),
)
