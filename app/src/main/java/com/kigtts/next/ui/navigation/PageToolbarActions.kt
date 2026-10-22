package com.kigtts.next.ui.navigation

import com.kigtts.next.ui.components.animateEnabled

import com.kigtts.next.ui.components.MotionIconButton
import com.kigtts.next.ui.components.MotionIconToggleButton
import com.kigtts.next.ui.components.keepColorsWhenLocked
import com.kigtts.next.ui.components.compactPlainIconColors

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.iconLongAction
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.delay

data class ToolbarAction(val id:String,val label:String,val icon:ImageVector,val enabled:Boolean=true,val preserveColorWhenLocked:Boolean=false,
    val checked:Boolean?=null,val longClick:(()->Unit)?=null,val click:()->Unit)
class PageToolbarActions {
    var source:State<List<ToolbarAction>>? by mutableStateOf(null)
    var primaryCount: State<Int>? by mutableStateOf(null)
    internal val loadingSources = mutableStateListOf<State<Boolean>>()
    val loading: Boolean get() = loadingSources.any { it.value }
    internal var backGuard: State<() -> Boolean>? by mutableStateOf(null)
    internal val input = PageInputState()
}
val LocalPageToolbarActions=compositionLocalOf<PageToolbarActions?> { null }

@Composable
fun RegisterPageLoading(loading: Boolean) {
    val host = LocalPageToolbarActions.current ?: return
    val source = rememberUpdatedState(loading)
    DisposableEffect(host, source) {
        host.loadingSources.add(source)
        onDispose { host.loadingSources.remove(source) }
    }
}

/** Each retained page owns its registration. Callback updates never replace the whole scaffold. */
@Composable
fun RegisterPageActions(actions:List<ToolbarAction>, primaryCount: Int = 2) {
    val host=LocalPageToolbarActions.current ?: return
    val source=rememberUpdatedState(actions)
    val count = rememberUpdatedState(primaryCount.coerceIn(0, 4))
    DisposableEffect(host,source) {
        host.source=source
        host.primaryCount = count
        onDispose { if(host.source===source) { host.source=null; host.primaryCount=null } }
    }
}

@Composable
fun RowScope.PageActionButtons(actions:List<ToolbarAction>) {
    var more by remember { mutableStateOf(false) }
    val active=LocalPageActive.current
    LaunchedEffect(active) { if(!active)more=false }
    val primaryCount = LocalPageToolbarActions.current?.primaryCount?.value ?: 2
    val wanted = actions.take(primaryCount)
    val retained = remember { mutableStateListOf<ToolbarAction>() }
    SideEffect { wanted.forEach { action ->
        val index = retained.indexOfFirst { it.id == action.id }
        if (index < 0) retained.add(action) else if (retained[index].label != action.label || retained[index].icon != action.icon || retained[index].enabled != action.enabled || retained[index].checked != action.checked) retained[index] = action
    } }
    retained.toList().forEach { stored -> key(stored.id) {
        val current = wanted.firstOrNull { it.id == stored.id }
        val action = current ?: stored
        LaunchedEffect(current != null) { if (current == null) { delay(220); retained.removeAll { it.id == stored.id } } }
        var shown by remember(stored.id) { mutableStateOf(false) }
        SideEffect { shown = current != null }
        val reveal by animateFloatAsState(if(shown)1f else 0f,tween(180),label="page-action-reveal")
        Box(Modifier.width((48f*reveal).dp).height(48.dp).clipToBounds().graphicsLayer { alpha=reveal }
            .testTag("page-action/" + stored.id).excludeCameraPull()) {
            val temporaryLock = action.preserveColorWhenLocked || !active
            val colors = compactPlainIconColors().let { if(temporaryLock)it.keepColorsWhenLocked() else it }
            if (action.checked != null) MotionIconToggleButton(action.checked, { action.click() },
                Modifier.requiredSize(48.dp).testTag(action.id), enabled = active && current != null && action.enabled,
                transientLock = temporaryLock) { Icon(action.icon, action.label) }
            else com.kigtts.next.ui.components.IconActionHint(action.label, action.longClick != null) {
                MotionIconButton(feedbackClick(action.click),Modifier.requiredSize(48.dp).testTag(action.id)
                    .iconLongAction(active&&current!=null&&action.enabled, action.longClick),enabled=active&&current!=null&&action.enabled,colors=colors,transientLock=temporaryLock) { Icon(action.icon,action.label) }
            }
        }
    } }
    AnimatedVisibility(actions.size>primaryCount, enter = fadeIn(tween(160)) + expandHorizontally(tween(180)),
        exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180))) {
    Box(Modifier.excludeCameraPull()) {
        MotionIconButton(feedbackClick { more=true },enabled=active,colors=compactPlainIconColors()) { Icon(Icons.Default.MoreVert,"更多页面操作") }
        DropdownMenu(more&&active,{ more=false }) {
            actions.drop(primaryCount).forEach { action -> DropdownMenuItem(text={ Text(action.label) },leadingIcon={ Icon(action.icon,null) },
                colors=MenuDefaults.itemColors().animateEnabled(action.enabled),
                enabled=action.enabled,onClick=feedbackClick { more=false;action.click() },modifier=Modifier.testTag(action.id)) }
        }
    }
    }
}
