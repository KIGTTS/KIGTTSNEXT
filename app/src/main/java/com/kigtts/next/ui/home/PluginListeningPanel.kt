package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kigtts.next.state.*
import com.kigtts.next.ui.navigation.LocalPageToolbarActions
import com.kigtts.next.ui.plugins.PluginBrowserScreen
import com.kigtts.next.ui.plugins.PluginPageScreen
import org.json.JSONObject

/** Reuses plugin pages inside the caption viewport, without capturing the application's title bar. */
@Composable
internal fun PluginListeningPanel(selection: ListeningPanelSelection, state: AppState, model: AppViewModel,
    modifier: Modifier = Modifier) {
    val page = runCatching { selection.plugin.page(selection.pageId, model.context()) }.getOrNull()
    if (page == null) { LaunchedEffect(selection) { model.listeningPanel.clear(selection.owner) }; return }
    val payload = JSONObject().put("transcript", state.transcript).put("listening", state.listening)
        .put("visible", state.captionsVisible).put("fontSize", state.captionTextSize).toString()
        .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    val update = "(()=>{const data=$payload;window.KigttsListening?.update?.(data);window.dispatchEvent(new CustomEvent('kigtts-listening',{detail:data}));})()"
    Box(modifier.testTag("plugin-listening-panel")) {
        CompositionLocalProvider(LocalPageToolbarActions provides null) {
            PluginPageScreen(page, selection.owner in state.busyPlugins,
                resolve = { resource -> if (resource.pluginId == null || resource.pluginId == selection.owner)
                    model.pluginManager.paths.existingResource(selection.owner, resource.path) else null },
                browser = { block -> PluginBrowserScreen(model, selection.owner, "listening/" + selection.pageId, block, update,
                    transparentBackground = !selection.replaceContent) }) { action, inputs ->
                model.invoke(selection.owner, action, inputs)
            }
        }
    }
}
