package com.kigtts.next.state

import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ListeningPanelSelection(val owner: String, val plugin: KigPlugin,
    val pageId: String, val replaceContent: Boolean, val replaceControls: Boolean)

/** A secondary page is scoped to one live worker, independently of the active ASR service. */
internal class ListeningPanelState {
    private val current = MutableStateFlow<ListeningPanelSelection?>(null)
    val selection = current.asStateFlow()
    fun select(plugin: KigPlugin, request: PluginOutcome.SetListeningPanel, context: PluginContext) {
        val page = request.pageId ?: return clear(plugin.descriptor.id)
        require(page.isNotBlank() && page.length <= 160 && plugin.page(page, context) != null) { "副面板页面不存在" }
        current.value = ListeningPanelSelection(plugin.descriptor.id, plugin, page, request.replaceContent, request.replaceControls)
    }
    fun clear(owner: String? = null) { if (owner == null || current.value?.owner == owner) current.value = null }
    fun reconcile(registry: PluginRegistry) {
        current.value?.let { selected ->
            if (registry.plugins.none { it === selected.plugin && registry.available(it) }) clear()
        }
    }
}
