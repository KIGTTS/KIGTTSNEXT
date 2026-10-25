package com.kigtts.next.overlay

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class OverlayNotice(val owner: String, val publisher: String, val text: String,
    val label: String?, val symbol: String, val shortcut: OverlayShortcut?)

/** Registration, user approval and transient bubbles are separate, bounded pieces of state. */
internal class OverlayHub private constructor(context: Context) {
    private val application = context.applicationContext
    private val store = OverlayStore.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val entries = MutableStateFlow<List<OverlayShortcut>>(emptyList()); val candidates = entries.asStateFlow()
    private var discovered = emptyList<OverlayShortcut>()
    private var registered = emptyList<OverlayShortcut>()
    private var activePublishers: Map<String, String>? = null
    private val pending = MutableStateFlow<List<OverlayShortcut>>(emptyList()); val requests = pending.asStateFlow()
    private val notice = MutableStateFlow<OverlayNotice?>(null); val bubble = notice.asStateFlow()
    private var expiry: Job? = null
    fun register(entry: OverlayShortcut) {
        require(registered.count { it.owner == entry.owner && it.id != entry.id } < 100) { "每个插件最多注册100个入口" }
        registered = registered.filterNot { it.id == entry.id } + entry
        publish()
    }
    fun discovered(entries: List<OverlayShortcut>, publishers: Map<String, String>) {
        discovered = entries; activePublishers = publishers; publish()
    }
    fun refreshBadges(owner: String, publisher: String, ui: com.kigtts.plugin.api.PluginContributions) {
        registered = registered.map { entry ->
            if (entry.owner != owner || entry.publisher != publisher) entry else entry.copy(badge = (
                ui.actions.firstOrNull { it.id == entry.action }?.badge ?: ui.settingsEntries.firstOrNull {
                    entry.target == "page:${it.pageId}" || entry.title == it.label
                }?.badge)?.toOverlayBadge())
        }
    }
    private fun publish() {
        val manual = registered.map { item ->
            if (activePublishers == null || activePublishers?.get(item.owner) == item.publisher) item
            else item.copy(enabled = false, badge = null)
        }
        entries.value = (manual + discovered).distinctBy { it.id }
    }
    fun requestAdd(entry: OverlayShortcut): Boolean {
        if (store.settings.value.shortcuts.any { it.id == entry.id && it.publisher == entry.publisher }) return false
        require(store.settings.value.shortcuts.size < 100) { "快捷入口已满，请先移除一个入口" }
        require(pending.value.size < 8) { "待确认入口过多" }
        if (pending.value.none { it.id == entry.id }) pending.value += entry
        return true
    }
    fun answer(id: String, allow: Boolean) {
        val entry = pending.value.firstOrNull { it.id == id } ?: return
        pending.value = pending.value.filterNot { it.id == id }
        if (allow) store.update { it.copy(shortcuts = it.shortcuts.filterNot { old -> old.id == id } + entry) }
    }
    fun show(value: OverlayNotice, duration: Long) {
        expiry?.cancel(); notice.value = value
        expiry = scope.launch { delay(duration.coerceIn(2000, 30_000)); if (notice.value === value) notice.value = null }
    }
    fun clear(owner: String) { if (notice.value?.owner == owner) { expiry?.cancel(); notice.value = null } }
    fun removeOwner(owner: String) {
        registered = registered.filterNot { it.owner == owner }
        discovered = discovered.filterNot { it.owner == owner }; publish()
        pending.value = pending.value.filterNot { it.owner == owner }; clear(owner)
        // Keep the installed shortcut visible so users can see what became unavailable.
        store.update { state -> state.copy(shortcuts = state.shortcuts.map { item ->
            if (item.owner == owner) item.copy(enabled = false) else item
        }) }
    }
    companion object {
        @Volatile private var instance: OverlayHub? = null
        fun get(context: Context): OverlayHub = instance?.takeIf { it.application === context.applicationContext } ?: synchronized(this) {
            instance?.takeIf { it.application === context.applicationContext } ?: OverlayHub(context.applicationContext).also { instance = it }
        }
    }
}
