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
    back: State<ReturnProgress>, drawerActive: Boolean,
    home: @Composable (Boolean) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val current = navigation.current
    val scope = rememberCoroutineScope()
    val pull = remember(model) { CameraPullState(scope, {
        model.cardCapture.open(systemCamera = false); (model.state.value.destination as? Destination.CardCamera)?.requestId
    }, model::navigateBack, { (model.state.value.destination as? Destination.CardCamera)?.requestId }) }
    val latestNavigation = rememberUpdatedState(navigation)
    val latestDrawer = rememberUpdatedState(drawerActive)
    val latestState = rememberUpdatedState(state)
    val sideDrawer = remember(model) { HomeSideDrawer(scope, model::openSidePage, model::closeSidePage) }
    val quickSwipe by model.subtitleRenderer.quickSwipe.collectAsState()
    val swipeExclusions = remember(model) { HomeSwipeExclusions() }
    val camera = navigation.pages.lastOrNull { it.cameraRequestId != null &&
        (it == current || it.depth == current.depth + 1 && it !in navigation.wanted) }
    val parent = camera?.let { navigation.wanted.lastOrNull { page -> page.depth == it.depth - 1 } }
    CompositionLocalProvider(LocalCameraPullState provides pull, LocalHomeSwipeExclusions provides swipeExclusions) {
    Box(Modifier.fillMaxSize().clipToBounds().homeSideDrawer(sideDrawer,
        when {
            quickSwipe && state.forcedFullscreen -> ({ model.exitSubtitleFullscreen(); model.openPanel(HomePanel.QUICK_TEXT) })
            else -> null
        },
        pagesEnabled = !state.forcedFullscreen, leftPageEnabled = false) {
        val value = latestState.value
        value.destination == Destination.Home && value.sidePage == null && !value.editing && !value.quickTextEditing &&
            !value.voiceInputActive && (!value.forcedFullscreen || quickSwipe) &&
            !latestDrawer.value && !latestNavigation.value.outgoingAbove && back.value.progress == 0f && !pull.active
    }.cameraPullNavigation(pull) {
        val value = latestNavigation.value; val foreground = value.current
        if (latestDrawer.value || value.outgoingAbove || back.value.progress > 0f) null
        else if (foreground.scene == PageScene.Home && latestState.value.let {
                it.sidePage == null && !it.editing && !it.quickTextEditing && !it.voiceInputActive && !it.forcedFullscreen
            }) CameraPullOrigin(CameraPullMode.ENTER, foreground.identity, horizontal = true)
        else if (foreground.isCardsBrowser) CameraPullOrigin(CameraPullMode.ENTER, foreground.identity)
        else foreground.cameraRequestId?.let { request -> value.wanted.getOrNull(value.wanted.lastIndex - 1)?.let {
            CameraPullOrigin(CameraPullMode.EXIT, it.identity, request, horizontal = true)
        } }
    }) {
        val previous = navigation.wanted.getOrNull(navigation.wanted.lastIndex - 1)
        navigation.pages.forEach { entry -> key(entry) {
            val foreground = entry == current
            val role = if (foreground) PageRole.FOREGROUND else if (entry in navigation.wanted) PageRole.BACKGROUND else PageRole.EXITING
            val active = foreground && !navigation.outgoingAbove && !drawerActive && !sideDrawer.active
            val cameraPose = if (pull.active && (entry.cameraRequestId == pull.cameraRequestId || entry.identity == pull.parentIdentity))
                remember(pull, entry) { CameraPagePose(pull, entry.cameraRequestId != null) } else null
            PageMotion(entry, role, active,
                { if (foreground && entry.scene != PageScene.Home) back.value else IdleReturnProgress.value },
                { if (entry == previous) back.value.progress else 0f },
                {
                    navigation.retention.retire(entry)
                    // Covered pages remain wanted. Only a truly retired visit loses its draft/ACK
                    // registry; reopening the same plugin page must not replay an old save return.
                    if(latestNavigation.value.wanted.none {it.identity==entry.identity})holder.removeState(entry.identity)
                }, vertical = pull.active && !pull.horizontal && (entry == camera || entry == parent),
                cameraPose = cameraPose,
                drawerPose = if (sideDrawer.active && (entry.scene == PageScene.Home || entry.scene == PageScene.Side(sideDrawer.page!!)))
                    remember(entry, sideDrawer) { SideDrawerPose(sideDrawer, entry.scene != PageScene.Home) } else null) {
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
