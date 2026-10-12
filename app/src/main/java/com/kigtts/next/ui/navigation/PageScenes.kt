package com.kigtts.next.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.kigtts.next.state.*

sealed interface PageScene {
    data object Home : PageScene
    data class Screen(val destination: Destination) : PageScene
    data class Side(val page: SidePage) : PageScene
}

data class PageEntry(val scene: PageScene, val depth: Int,
    val direction: Float = if (scene == PageScene.Side(SidePage.SKETCH)) -1f else 1f) {
    val identity: String get() = "$depth/" + when (val page = scene) {
        PageScene.Home -> "home"
        is PageScene.Side -> "side/" + page.page.name.lowercase()
        is PageScene.Screen -> when (val destination = page.destination) {
            Destination.Home -> "home"
            Destination.Settings -> "settings"
            is Destination.SettingsDetail -> "settings/" + destination.category.name
            Destination.Plugins -> "plugins"
            is Destination.PluginPage -> destination.pluginId + "/" + destination.pageId
            is Destination.MediaLibrary -> "media/" + destination.target
            is Destination.HtmlFiles -> "html-files/" + destination.mediaId
            is Destination.HtmlPreview -> "html-preview/" + destination.mediaId
            is Destination.ContentEditor -> "content-editor/" + destination.token
            is Destination.PhraseEditor -> "phrase-editor/" + destination.token
        }
    }
}

private fun pageTrail(state: AppState): List<PageEntry> = buildList {
    add(PageScene.Home)
    state.sidePage?.let { add(PageScene.Side(it)) }
    if (state.destination != Destination.Home) {
        (state.navigationStack + state.destination).filterNot { it == Destination.Home }.forEach { add(PageScene.Screen(it)) }
    }
}.mapIndexed { index, scene -> PageEntry(scene, index) }

@Stable
class PageRetention(initial: List<PageEntry>) {
    var pages by mutableStateOf(initial); private set
    private var wanted = initial
    fun reconcile(target: List<PageEntry>) {
        wanted = target
        pages = (pages + target).distinct().sortedBy { it.depth }
    }
    fun retire(entry: PageEntry) { if (entry !in wanted) pages = pages - entry }
}

data class PageNavigation(val wanted: List<PageEntry>, val pages: List<PageEntry>, val retention: PageRetention) {
    val current get() = wanted.last()
    val outgoingAbove get() = pages.any { it !in wanted && it.depth >= current.depth }
    val returningHome get() = current.scene == PageScene.Home && outgoingAbove
}

@Composable
fun rememberPageNavigation(state: AppState): PageNavigation {
    val landscape=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE
    val directions=rememberSaveable { mutableMapOf<String,Float>() }
    val wanted = remember(state.destination, state.sidePage, state.navigationStack) { pageTrail(state).map { entry ->
        entry.copy(direction=directions.getOrPut(entry.identity) {
            if(landscape&&entry.scene==PageScene.Side(SidePage.SKETCH))1f else entry.direction
        })
    } }
    val retention = remember { PageRetention(wanted) }
    val pages = (retention.pages + wanted).distinct().sortedBy { it.depth }
    SideEffect { retention.reconcile(wanted);directions.keys.retainAll(pages.map { it.identity }.toSet()) }
    return PageNavigation(wanted, pages, retention)
}
