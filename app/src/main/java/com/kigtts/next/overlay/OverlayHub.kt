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
    private val pending = MutableStateFlow<List<OverlayShortcut>>(emptyList()); val requests = pending.asStateFlow()
    private val notice = MutableStateFlow<OverlayNotice?>(null); val bubble = notice.asStateFlow()
    private var expiry: Job? = null
    fun register(entry: OverlayShortcut) {
        require(entries.value.count { it.owner == entry.owner && it.id != entry.id } < 12) { "每个插件最多注册12个入口" }
        entries.value = entries.value.filterNot { it.id == entry.id } + entry
    }
    fun requestAdd(entry: OverlayShortcut): Boolean {
        if (store.settings.value.shortcuts.any { it.id == entry.id && it.publisher == entry.publisher }) return false
        require(store.settings.value.shortcuts.size < 24) { "快捷入口已满，请先移除一个入口" }
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
        entries.value = entries.value.filterNot { it.owner == owner }
        pending.value = pending.value.filterNot { it.owner == owner }; clear(owner)
    }
    companion object {
        @Volatile private var instance: OverlayHub? = null
        fun get(context: Context): OverlayHub = instance?.takeIf { it.application === context.applicationContext } ?: synchronized(this) {
            instance?.takeIf { it.application === context.applicationContext } ?: OverlayHub(context.applicationContext).also { instance = it }
        }
    }
}
