package com.kigtts.next.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.overlay.*
import com.kigtts.next.ui.components.ReadablePage
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.rememberDragFeedback
import com.kigtts.next.ui.management.managedItemShape
import com.kigtts.next.ui.components.reorder.*

/** One stable pointer owner, animated neighbors, and a single durable write on release. */
@Composable internal fun OverlaySettingsList(entries: List<OverlayShortcut>, order: (List<String>) -> Unit,
    edit: (OverlayShortcut) -> Unit, add: () -> Unit, controls: @Composable () -> Unit,
    remove: (OverlayShortcut) -> Unit = {}) {
    val keys = entries.map { it.id }
    var positions by remember { mutableStateOf(keys) }
    var revealed by remember { mutableStateOf<String?>(null) }
    var held by remember { mutableStateOf<String?>(null) }
    var holdRequest by remember { mutableIntStateOf(0) }
    val bounds = remember { mutableMapOf<String, LayoutCoordinates>() }
    var root by remember { mutableStateOf(Offset.Zero) }
    val feedback = rememberDragFeedback()
    val drag = rememberReorderDrag({ bounds.filter { it.key in positions && it.value.isAttached }.map { (id, coordinates) ->
        ReorderTarget(id, Rect(coordinates.positionInRoot() - root, Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())))
    } }, verticalOnly = true, animateDrop = true, feedback = feedback) { from, to ->
        val items = positions.toMutableList(); val a = items.indexOf(from); val b = items.indexOf(to)
        if (a >= 0 && b >= 0 && a != b) { items.add(b, items.removeAt(a)); positions = items; feedback.step() }
    }
    val drop = rememberReorderDrop(drag)
    val latestOrder by rememberUpdatedState(order)
    LaunchedEffect(drag.settling) { if (drag.settling && positions != keys) latestOrder(positions) }
    LaunchedEffect(keys, drag.key) {
        positions = if (drag.key == null) keys else positions.filter { it in keys } + keys.filterNot { it in positions }
    }
    val state = rememberLazyListState()
    LaunchedEffect(state.isScrollInProgress, drag.key) {
        if (state.isScrollInProgress || drag.key != null) revealed = null
    }
    val density = LocalDensity.current
    val elevation by animateDpAsState(if (drag.key != null && !drag.settling) 6.dp else 0.dp, tween(180), label = "overlay-entry-lift")
    val byId = entries.associateBy { it.id }
    val floatingPosition = positions.indexOf(drag.key).let { index ->
        when { positions.size == 1 -> SettingPosition.SINGLE; index == 0 -> SettingPosition.FIRST
            index == positions.lastIndex -> SettingPosition.LAST; else -> SettingPosition.MIDDLE }
    }
    val floatingShape = managedItemShape(floatingPosition, false)
    @Composable fun Entry(id: String, modifier: Modifier, grip: Modifier, elevated: Boolean = false) {
        val entry = byId[id] ?: return
        val index = positions.indexOf(id)
        OverlaySettingsRow(entry,
            if (positions.size == 1) SettingPosition.SINGLE else if (index == 0) SettingPosition.FIRST else if (index == positions.lastIndex) SettingPosition.LAST else SettingPosition.MIDDLE,
            modifier = modifier, grip = grip, elevated = elevated, active = revealed == id,
            holdRequest = if (held == id) holdRequest else 0,
            activate = { revealed = id }, edit = { edit(entry) }, remove = { remove(entry) }, move = { delta ->
                    val items = positions.toMutableList(); val i = items.indexOf(id)
                    if (i + delta in items.indices) { items.add(i + delta, items.removeAt(i)); latestOrder(items) }
            })
    }
    ReadablePage(840.dp) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { root = it.positionInRoot() }.reorderGestures(drag,
            onStationaryHold = { target -> held = target.key; revealed = target.key; holdRequest++ },
            onDragStart = { revealed = null; held = null })) {
            LazyColumn(Modifier.fillMaxSize().testTag("overlay-shortcuts-list"), state = state,
                contentPadding = com.kigtts.next.ui.navigation.pageContentPadding(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item(key = "controls") { Column(Modifier.padding(bottom = 20.dp)) { controls() } }
                item(key = "title") { Text("快捷入口", Modifier.padding(8.dp), style = MaterialTheme.typography.titleMedium) }
                items(positions, key = { it }) { id ->
                    DisposableEffect(id) { onDispose { bounds.remove(id) } }
                    Entry(id, Modifier.fillMaxWidth().testTag("overlay-entry:$id").animateItem(fadeInSpec = null, fadeOutSpec = null,
                        placementSpec = if (drag.key == id) null else tween(180)).onGloballyPositioned { bounds[id] = it }
                        .then(if (drag.key == id) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier), Modifier.reorderGrip(id, drag))
                }
                item(key = "add") { FilledTonalButton(onClick = feedbackClick(add), Modifier.fillMaxWidth().padding(top = 12.dp), enabled = entries.size < 100) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("添加入口")
                } }
            }
            ReorderAutoScroll(drag, { state.layoutInfo.viewportSize.height.toFloat() }, { state.scrollBy(it) })
            drag.key?.takeIf { it in byId }?.let { id ->
                Entry(id, Modifier.width(with(density) { drag.floatingBounds.width.toDp() }).graphicsLayer {
                    translationX = drop.value.x; translationY = drop.value.y; shadowElevation = elevation.toPx()
                    shape = floatingShape
                }.testTag("overlay-floating-entry").clearAndSetSemantics { }, Modifier, elevated = true)
            }
        }
    }
}
