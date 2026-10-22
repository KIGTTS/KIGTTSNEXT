package com.kigtts.next.state

import com.kigtts.next.media.WebContentPolicy
import com.kigtts.plugin.api.KigPlugin
import com.kigtts.plugin.api.PluginRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** A renderer belongs to one live plugin generation and never changes the editable document. */
class SubtitleRendererSelection internal constructor(
    val owner: String,
    val root: File,
    val mainPage: String,
    internal val plugin: KigPlugin,
)

class SubtitleRendererState {
    private val mutableSelection = MutableStateFlow<SubtitleRendererSelection?>(null)
    val selection = mutableSelection.asStateFlow()

    /** The caller resolves root through this owner's PluginStorage before selecting it. */
    fun select(owner: String, plugin: KigPlugin, root: File, mainPage: String = "index.html") {
        require(plugin.descriptor.id == owner) { "Renderer owner does not match plugin" }
        val directory = root.canonicalFile
        require(directory.isDirectory && WebContentPolicy.file(directory, mainPage) != null) { "Renderer main page is unavailable" }
        val current = mutableSelection.value
        if (current?.owner == owner && current.plugin === plugin && current.root == directory && current.mainPage == mainPage) return
        mutableSelection.value = SubtitleRendererSelection(owner, directory, mainPage, plugin)
    }

    /** A plugin can only turn off its own renderer. The host may clear any renderer. */
    fun clear(owner: String? = null) {
        if (owner == null || mutableSelection.value?.owner == owner) mutableSelection.value = null
    }

    fun reconcile(registry: PluginRegistry) {
        val current = mutableSelection.value ?: return
        if (registry.plugins.none { it === current.plugin } || WebContentPolicy.file(current.root, current.mainPage) == null) clear()
    }
}
