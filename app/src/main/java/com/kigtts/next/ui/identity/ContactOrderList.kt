package com.kigtts.next.ui.identity

import com.kigtts.next.i18n.uiText

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.identity.ContactLink
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.components.reorder.*

@Composable internal fun ContactOrderList(links: List<ContactLink>, change: (List<ContactLink>) -> Unit,
    edit: (Int) -> Unit, remove: (Int) -> Unit) {
    val occurrences = mutableMapOf<ContactLink, Int>()
    val keys = links.map { link ->
        val occurrence = occurrences.getOrDefault(link, 0); occurrences[link] = occurrence + 1
        "${link.label}\u0000${link.value}\u0000${link.qrContent}\u0000$occurrence"
    }
    val byId = keys.zip(links).toMap()
    var order by remember { mutableStateOf(keys) }
    val latestChange by rememberUpdatedState(change)
    val bounds = remember { mutableMapOf<String, LayoutCoordinates>() }
    var root by remember { mutableStateOf(Offset.Zero) }
    val feedback = rememberDragFeedback()
    val drag = rememberReorderDrag({ bounds.filter { it.key in keys && it.value.isAttached }.map { (id, c) ->
        ReorderTarget(id, Rect(c.positionInRoot() - root, Size(c.size.width.toFloat(), c.size.height.toFloat())))
    } }, verticalOnly = true, animateDrop = true, feedback = feedback) { from, to ->
        val items = order.toMutableList(); val a = items.indexOf(from); val b = items.indexOf(to)
        if (a >= 0 && b >= 0 && a != b) { items.add(b, items.removeAt(a)); order = items; feedback.step() }
    }
    val drop by rememberReorderDrop(drag)
    LaunchedEffect(drag.settling) { if (drag.settling && order != keys) latestChange(order.mapNotNull(byId::get)) }
    LaunchedEffect(keys, drag.key) {
        order = if (drag.key == null) keys else order.filter { it in keys } + keys.filterNot { it in order }
    }
    val state = rememberLazyListState(); val density = LocalDensity.current
    @Composable fun Entry(id: String, modifier: Modifier, grip: Modifier) {
        val link = byId[id] ?: return; val index = keys.indexOf(id)
        Surface(onClick = feedbackClick { edit(index) }, modifier = modifier.height(72.dp), shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SocialContactIcon(link, Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(link.label, style = MaterialTheme.typography.labelLarge)
                    Text(link.value, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(feedbackClick { remove(index) }, Modifier.size(40.dp)) { Icon(Icons.Default.Close, uiText("accessibility.remove_item", "title" to link.label), Modifier.size(22.dp)) }
                val reorder = uiText("overlay.row.drag", "title" to link.label)
                val moveUp = uiText("overlay.row.up")
                val moveDown = uiText("overlay.row.down")
                Box(Modifier.size(40.dp).then(grip).semantics {
                    contentDescription = reorder
                    customActions = listOf(-1 to moveUp, 1 to moveDown).map { (step, label) -> CustomAccessibilityAction(label) {
                        val items = links.toMutableList(); val target = index + step
                        if (target in items.indices) { items.add(target, items.removeAt(index)); latestChange(items) }; true
                    } }
                }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null, Modifier.size(22.dp)) }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height((links.size * 76).coerceAtMost(304).dp).testTag("contact-order-list")
        .onGloballyPositioned { root = it.positionInRoot() }.reorderGestures(drag)) {
        LazyColumn(Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(order, key = { it }) { id ->
                DisposableEffect(id) { onDispose { bounds.remove(id) } }
                Entry(id, Modifier.fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (drag.key == id) null else tween(180))
                    .onGloballyPositioned { bounds[id] = it }.then(if (drag.key == id) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier), Modifier.reorderGrip(id, drag))
            }
        }
        ReorderAutoScroll(drag, { state.layoutInfo.viewportSize.height.toFloat() }, { state.scrollBy(it) })
        drag.key?.let { id -> Entry(id, Modifier.width(with(density) { drag.floatingBounds.width.toDp() })
            .graphicsLayer { translationX = drop.x; translationY = drop.y }.clearAndSetSemantics { }, Modifier) }
    }
}
