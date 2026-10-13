package com.kigtts.next.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import com.kigtts.next.state.*
import com.kigtts.next.ui.home.SecondarySidePage
import com.kigtts.next.ui.sketch.SketchState

@Composable
fun PageNavigationHost(navigation: PageNavigation, state: AppState, model: AppViewModel,
    sketch: SketchState, shareRequest: Int, shareConsumed: () -> Unit, shareSketch: () -> Unit,
    progress: Float, edge: Int, drawerActive: Boolean,
    home: @Composable (Boolean) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val current = navigation.current
    Box(Modifier.fillMaxSize().clipToBounds()) {
        navigation.pages.forEach { entry -> key(entry) {
            val foreground = entry == current
            val role = if (foreground) PageRole.FOREGROUND else if (entry in navigation.wanted) PageRole.BACKGROUND else PageRole.EXITING
            val active = foreground && !navigation.outgoingAbove && !drawerActive
            PageMotion(entry, role, active, if (foreground) progress else 0f, edge,
                if (entry == navigation.wanted.getOrNull(navigation.wanted.lastIndex - 1)) progress else 0f,
                { navigation.retention.retire(entry) }) {
                holder.SaveableStateProvider(entry.identity) {
                    when (val scene = entry.scene) {
                        PageScene.Home -> home(active)
                        is PageScene.Screen -> Box(Modifier.fillMaxSize()
                            .then(if (foreground) Modifier.testTag("navigation-secondary") else Modifier)) {
                            SecondaryDestination(scene.destination, state, model)
                        }
                        is PageScene.Side -> Box(Modifier.fillMaxSize().testTag("secondary-" + scene.page.name.lowercase())) {
                            SecondarySidePage(scene.page, model::closeSidePage, sketch, shareRequest, shareConsumed, shareSketch, model::message, model)
                        }
                    }
                }
            }
        } }
    }
}
