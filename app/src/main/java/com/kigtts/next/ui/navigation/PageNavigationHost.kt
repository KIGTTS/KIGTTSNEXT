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
    val scope = rememberCoroutineScope()
    val pull = remember(model) { CameraPullState(scope, {
        model.cardCapture.open(); (model.state.value.destination as? Destination.CardCamera)?.requestId
    }, model::navigateBack, { (model.state.value.destination as? Destination.CardCamera)?.requestId }) }
    val latestNavigation = rememberUpdatedState(navigation)
    val latestDrawer = rememberUpdatedState(drawerActive)
    val latestProgress = rememberUpdatedState(progress)
    val camera = navigation.pages.lastOrNull { it.cameraRequestId != null &&
        (it == current || it.depth == current.depth + 1 && it !in navigation.wanted) }
    val parent = camera?.let { navigation.wanted.lastOrNull { page -> page.depth == it.depth - 1 } }
    CompositionLocalProvider(LocalCameraPullState provides pull) {
    Box(Modifier.fillMaxSize().clipToBounds().cameraPullNavigation(pull) {
        val value = latestNavigation.value; val foreground = value.current
        if (latestDrawer.value || value.outgoingAbove || latestProgress.value > 0f) null
        else if (foreground.isCardsBrowser) CameraPullOrigin(CameraPullMode.ENTER, foreground.identity)
        else foreground.cameraRequestId?.let { request -> value.wanted.getOrNull(value.wanted.lastIndex - 1)?.let {
            CameraPullOrigin(CameraPullMode.EXIT, it.identity, request)
        } }
    }) {
        navigation.pages.forEach { entry -> key(entry) {
            val foreground = entry == current
            val role = if (foreground) PageRole.FOREGROUND else if (entry in navigation.wanted) PageRole.BACKGROUND else PageRole.EXITING
            val active = foreground && !navigation.outgoingAbove && !drawerActive
            val cameraPose = if (pull.active && (entry.cameraRequestId == pull.cameraRequestId || entry.identity == pull.parentIdentity))
                remember(pull, entry) { CameraPagePose(pull, entry.cameraRequestId != null) } else null
            PageMotion(entry, role, active, if (foreground) progress else 0f, edge,
                if (entry == navigation.wanted.getOrNull(navigation.wanted.lastIndex - 1)) progress else 0f,
                {
                    navigation.retention.retire(entry)
                    // Covered pages remain wanted. Only a truly retired visit loses its draft/ACK
                    // registry; reopening the same plugin page must not replay an old save return.
                    if(latestNavigation.value.wanted.none {it.identity==entry.identity})holder.removeState(entry.identity)
                }, vertical = entry == camera || entry == parent,
                cameraPose = cameraPose) {
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
}
