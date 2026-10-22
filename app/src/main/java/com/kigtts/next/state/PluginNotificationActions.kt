package com.kigtts.next.state

import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.kigtts.next.notifications.NotificationRoutes
import kotlinx.coroutines.launch

/** Only a host-issued route for the same installed publisher can activate notification controls. */
internal fun AppViewModel.notificationLink(intent: Intent?) {
    val route = NotificationRoutes(getApplication()).resolve(intent) ?: return
    viewModelScope.launch {
        if (registry.plugin(route.owner) == null) refreshPlugins()
        val installed = pluginManager.status.singleOrNull { it.installed.manifest.id == route.owner }?.installed ?: return@launch
        if (!installed.enabled || installed.publisherSha256 != route.publisher) return@launch
        val plugin = registry.plugin(route.owner)?.takeIf(registry::available) ?: return@launch
        val action = route.action
        val actionId = action.actionId; val pageId = action.pageId
        when {
            action.phraseId != null -> phrases.value.firstOrNull { it.id == action.phraseId }?.let { navigate(Destination.Home); content.send(it) }
            actionId != null -> invoke(route.owner, actionId)
            pageId != null -> if (plugin.page(pageId, context()) != null) navigate(Destination.PluginPage(route.owner, pageId))
            action.target == "cards" -> openSidePage(SidePage.CARDS)
            action.target == "sketch" -> openSidePage(SidePage.SKETCH)
            action.target == "quick-text" -> { navigate(Destination.Home); openPanel(HomePanel.QUICK_TEXT) }
            action.target == "home" -> navigate(Destination.Home)
            else -> plugin.contributions.settingsEntries.firstOrNull()?.let { navigate(Destination.PluginPage(route.owner, it.pageId)) } ?: navigate(Destination.Home)
        }
    }
}
