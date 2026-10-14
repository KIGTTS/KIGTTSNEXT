@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.rememberDragFeedback
import com.kigtts.next.ui.components.reorder.*

/** Reorder locally while holding a pointer; one durable transaction is submitted on release. */
@Composable internal fun CardManagementSortPane(keys: List<String>, mode: CardManagementMode, columns: Int,
    enabled: Boolean, busy: Boolean, originId: String?, order: (List<String>) -> Unit,
    item: @Composable (String, Modifier, Modifier, Boolean) -> Unit) {
    var positions by remember(mode) { mutableStateOf(keys) }
    val bounds = remember { mutableMapOf<String, LayoutCoordinates>() }
    var root by remember { mutableStateOf(Offset.Zero) }
    val feedback = rememberDragFeedback()
    val drag = rememberReorderDrag({ bounds.filter { it.key in positions && it.value.isAttached }.map { (id, coordinates) ->
        ReorderTarget(id, Rect(coordinates.positionInRoot() - root,
            Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())))
    } },
        horizontalOnly = mode == CardManagementMode.STRIP, verticalOnly = mode == CardManagementMode.ALBUMS,
        animateDrop = true, feedback = feedback) { from, to ->
        val next = positions.toMutableList(); val a = next.indexOf(from); val b = next.indexOf(to)
        if (enabled && a >= 0 && b >= 0 && a != b) { next.add(b, next.removeAt(a)); positions = next; feedback.step() }
    }
    val dropped = rememberReorderDrop(drag)
    val currentOrder = rememberUpdatedState(order)
    LaunchedEffect(drag.settling) { if (enabled && drag.settling && positions != keys && positions.toSet() == keys.toSet()) currentOrder.value(positions) }
    LaunchedEffect(keys, drag.key, busy) {
        if (drag.key == null && !busy) positions = keys
        else positions = positions.filter { it in keys } + keys.filterNot { it in positions }
    }
    val density = LocalDensity.current
    val entry: @Composable (String, Modifier) -> Unit = { id, modifier ->
        DisposableEffect(id) { onDispose { bounds.remove(id) } }
        item(id, modifier.onGloballyPositioned { bounds[id] = it }
            .then(if (drag.key == id) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier),
            if (enabled) Modifier.reorderGrip(id, drag) else Modifier, false)
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("card-management-sort").onGloballyPositioned { root = it.positionInRoot() }
        .then(if (enabled) Modifier.reorderGestures(drag) else Modifier)) {
        val grid = rememberLazyGridState()
        val list = rememberLazyListState(initialFirstVisibleItemIndex =
            if (mode == CardManagementMode.STRIP) (keys.indexOfFirst { it == originId } - 1).coerceAtLeast(0)
            else keys.indexOfFirst { it == originId }.coerceAtLeast(0))
        if (mode == CardManagementMode.GRID) {
            LazyVerticalGrid(GridCells.Fixed(columns.coerceIn(2, 4)), Modifier.fillMaxSize().testTag("card-management-grid"), state = grid,
                contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(positions, key = { it }) { id -> entry(id, Modifier.fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null,
                    placementSpec = if (drag.key == id) null else MaterialTheme.motionScheme.defaultSpatialSpec())) }
            }
            ReorderAutoScroll(drag, { grid.layoutInfo.viewportSize.height.toFloat() }, { grid.scrollBy(it) })
        } else if (mode == CardManagementMode.STRIP) {
            val width = minOf(180.dp, maxHeight * .41f, maxWidth * .4f).coerceAtLeast(1.dp)
            LazyRow(Modifier.fillMaxSize().testTag("card-management-strip"), state = list,
                contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                itemsIndexed(positions, key = { _, id -> id }) { index, id -> entry(id, Modifier.width(width).animateItem(fadeInSpec = null, fadeOutSpec = null,
                    placementSpec = if (drag.key == id) null else MaterialTheme.motionScheme.defaultSpatialSpec())
                    .cardManagementPaper(index, positions.indexOfFirst { it == originId }.coerceAtLeast(0), LocalCardManagementReveal.current, width, 16.dp, id)) }
            }
            ReorderAutoScroll(drag, { list.layoutInfo.viewportSize.width.toFloat() }, { list.scrollBy(it) }, horizontal = true)
        } else {
            LazyColumn(Modifier.fillMaxSize().testTag("card-management-albums"), state = list,
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(positions, key = { it }) { id -> entry(id, Modifier.fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null,
                    placementSpec = if (drag.key == id) null else MaterialTheme.motionScheme.defaultSpatialSpec())) }
            }
            ReorderAutoScroll(drag, { list.layoutInfo.viewportSize.height.toFloat() }, { list.scrollBy(it) })
        }
        drag.key?.takeIf { it in positions }?.let { id ->
          CompositionLocalProvider(LocalPolaroidSharedScope provides null) {
            item(id, Modifier.width(with(density) { drag.floatingBounds.width.toDp() })
                .graphicsLayer { translationX = dropped.value.x; translationY = dropped.value.y }
                .clearAndSetSemantics { }, Modifier, true)
          }
        }
    }
}
