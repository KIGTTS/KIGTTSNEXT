@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.components.reorder.*

@Composable
internal fun QuickPhraseLayout(filtered: List<QuickPhrase>, singleRow: Boolean, columns: Int, model: AppViewModel, drag: ReorderDrag, active: Boolean) {
    val sources = remember(filtered.map { it.id }) { filtered.map { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }
    val currentRow by rememberUpdatedState(singleRow)
    val currentPhrases by rememberUpdatedState(filtered)
    val currentSources by rememberUpdatedState(sources)
    val currentPressed by rememberUpdatedState(pressed)
    val grid = rememberLazyGridState()
    val row = rememberLazyListState()
    val feedback = rememberDragFeedback()
    var root by remember { mutableStateOf(Offset.Zero) }
    val ids by rememberUpdatedState(filtered.map { it.id }.toSet())
    val provider = remember(grid, row, drag) { {
        (if (currentRow) row.layoutInfo.visibleItemsInfo.map { item ->
            ReorderTarget(item.key as String, Rect(item.offset.toFloat(), 0f,
                (item.offset + item.size).toFloat(), row.layoutInfo.viewportSize.height.toFloat()))
        } else grid.layoutInfo.visibleItemsInfo.map { item ->
            ReorderTarget(item.key as String, Rect(Offset(item.offset.x.toFloat(), item.offset.y.toFloat()),
                Size(item.size.width.toFloat(), item.size.height.toFloat())))
        })

            .filter { it.key in ids }.map { it.copy(bounds = it.bounds.translate(root - drag.origin)) }
    } }
    val latestMove by rememberUpdatedState<(String, String) -> Unit>({ from, to ->
        val before = model.phrases.value
        val anchorIndex = if (currentRow) row.firstVisibleItemIndex else grid.firstVisibleItemIndex
        val anchorOffset = if (currentRow) row.firstVisibleItemScrollOffset else grid.firstVisibleItemScrollOffset
        model.quickTexts.movePhrase(from, to)
        if (model.phrases.value != before) {
            // Reordering the leading item must not make LazyLayout follow its stable
            // key to another slot and scroll the whole list beneath the finger.
            if (currentRow) row.requestScrollToItem(anchorIndex, anchorOffset)
            else grid.requestScrollToItem(anchorIndex, anchorOffset)
            feedback.step()
        }
    })
    val moveProvider = remember(grid, row) { { from: String, to: String -> latestMove(from, to) } }
    if (active) DisposableEffect(drag, provider) {
        drag.targetProvider = provider; drag.moveProvider = moveProvider
        onDispose {
            if (drag.targetProvider === provider) drag.targetProvider = null
            if (drag.moveProvider === moveProvider) drag.moveProvider = null
        }
    }
    if (active) ReorderAutoScroll(drag,
        { if (currentRow) row.layoutInfo.viewportSize.width.toFloat() else grid.layoutInfo.viewportSize.height.toFloat() },
        { if (currentRow) row.scrollBy(it) else grid.scrollBy(it) }, horizontal = singleRow)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    Box(Modifier.fillMaxSize()) {
    AnimatedContent(singleRow, modifier = Modifier.fillMaxSize().clipToBounds().testTag("phrases-layout-transition"),
        label = "quick-text-layout", transitionSpec = {
            val direction = if (targetState) -1 else 1
            (fadeIn(effects) + slideInVertically(spatial) { direction * it / 8 })
                .togetherWith(fadeOut(effects) + slideOutVertically(spatial) { -direction * it / 8 }).using(null)
        }) { isRow ->
        val filtered = currentPhrases; val sources = currentSources; val pressed = currentPressed
        val listModifier = Modifier.fillMaxSize().onGloballyPositioned { root = it.positionInRoot() }
            .then(if (active && isRow == currentRow) Modifier else Modifier.clearAndSetSemantics {})
        if (isRow) LazyRow(listModifier.testTag("phrases-horizontal"), state = row, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(filtered, key = { _, phrase -> phrase.id }) { index, phrase ->
                PhraseTile(phrase, { model.content.send(phrase) }, CornerNeighbors(start = index > 0, end = index < filtered.lastIndex),
                    pressed[index], pressed.getOrElse(index - 1) { false } || pressed.getOrElse(index + 1) { false },
                    sources[index], Modifier.width(164.dp).fillParentMaxHeight()
                        .animateItem(fadeInSpec = null, fadeOutSpec = null,
                            placementSpec = if (drag.key == phrase.id) null else MaterialTheme.motionScheme.defaultSpatialSpec())
                        .then(if (drag.key == phrase.id) Modifier.alpha(0f) else Modifier), model.mediaRepository, hidden = drag.key == phrase.id)
            }
        } else LazyVerticalGrid(GridCells.Fixed(columns), state = grid, modifier = listModifier.testTag("phrases-vertical"),
            verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(filtered, key = { _, phrase -> phrase.id }) { index, phrase ->
                val neighbors = listOf(index - columns, index + columns) +
                    listOfNotNull((index - 1).takeIf { index % columns > 0 }, (index + 1).takeIf { index % columns < columns - 1 })
                PhraseTile(phrase, { model.content.send(phrase) },
                    CornerNeighbors(start = index % columns > 0, end = index % columns < columns - 1 && index < filtered.lastIndex,
                        above = index >= columns, below = index + columns <= filtered.lastIndex),
                    pressed[index], neighbors.any { pressed.getOrElse(it) { false } }, sources[index],
                    Modifier.height(QuickPhraseHeight).animateItem(fadeInSpec = null, fadeOutSpec = null,
                        placementSpec = if (drag.key == phrase.id) null else MaterialTheme.motionScheme.defaultSpatialSpec())
                        .then(if (drag.key == phrase.id) Modifier.alpha(0f) else Modifier), model.mediaRepository, hidden = drag.key == phrase.id)
            }
        }
    }
    EmptyContent(filtered.isEmpty(), Icons.Default.TextFields, "还没有快捷文本", description = "点击编辑添加")
    }
}

