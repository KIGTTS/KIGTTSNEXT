@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import com.kigtts.next.ui.components.reorder.*

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*

@Composable
fun EditablePhraseGrid(phrases: List<QuickPhrase>, model: AppViewModel, edit: (QuickPhrase) -> Unit,
    delete: (QuickPhrase) -> Unit, add: () -> Unit, modifier: Modifier,
    drag: ReorderDrag, active: Boolean = true, hiddenPhraseId: String? = null,
    onBounds: (String, PhraseDialogAnchor) -> Unit = { _, _ -> }, onAddBounds: (PhraseDialogAnchor) -> Unit = {}, columns: Int = 2) {
    val grid = rememberLazyGridState()
    var viewportRestore by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(phrases.map { it.id }) {
        viewportRestore?.let { (index, offset) -> grid.requestScrollToItem(index, offset); viewportRestore = null }
    }
    val ids by rememberUpdatedState(phrases.map { it.id }.toSet())
    var root by remember { mutableStateOf(Offset.Zero) }
    fun targets() = grid.layoutInfo.visibleItemsInfo.mapNotNull { item ->
        (item.key as? String)?.takeIf { it.startsWith("phrase/") }?.removePrefix("phrase/")?.takeIf { it in ids }?.let {
            ReorderTarget(it, Rect(Offset(item.offset.x.toFloat(), (item.offset.y + grid.layoutInfo.beforeContentPadding).toFloat()),
                Size(item.size.width.toFloat(), item.size.height.toFloat())))
        }
    }
    val move: (String, String) -> Unit = { from, to ->
        if (viewportRestore == null) viewportRestore = grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset
        model.quickTexts.movePhrase(from, to)
    }
    val latestMove by rememberUpdatedState(move)
    val provider = remember(grid, drag) { { targets().map { it.copy(bounds = it.bounds.translate(root - drag.origin)) } } }
    val moveProvider = remember(grid) { { from: String, to: String -> latestMove(from, to) } }
    if (active) DisposableEffect(drag, provider) {
        drag.targetProvider = provider; drag.moveProvider = moveProvider
        onDispose {
            if (drag.targetProvider === provider) drag.targetProvider = null
            if (drag.moveProvider === moveProvider) drag.moveProvider = null
        }
    }
    LazyVerticalGrid(GridCells.Fixed(columns), state = grid,
        modifier = modifier.fillMaxWidth().testTag("editable-phrases").onGloballyPositioned { root = it.positionInRoot() },
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(phrases, key = { _, item -> "phrase/" + item.id }) { index, phrase ->
            val previous = phrases.getOrNull(index - 1); val next = phrases.getOrNull(index + 1)
            val actions = Modifier.semantics {
                customActions = listOfNotNull(
                    previous?.let { CustomAccessibilityAction("向前移动条目") { model.quickTexts.movePhrase(phrase.id, it.id); true } },
                    next?.let { CustomAccessibilityAction("向后移动条目") { model.quickTexts.movePhrase(phrase.id, it.id); true } })
            }
            val neighbors = CornerNeighbors(start = index % columns > 0, end = index % columns < columns - 1 && index < phrases.lastIndex,
                above = index >= columns, below = index + columns < phrases.size)
            EditablePhraseCard(phrase, { edit(phrase) }, { delete(phrase) }, neighbors,
                Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null,
                    placementSpec = if (drag.key == phrase.id) null else MaterialTheme.motionScheme.defaultSpatialSpec())
                    .then(if (drag.key == phrase.id || hiddenPhraseId == phrase.id) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier)
                    .then(actions).testTag("edit-phrase-" + phrase.id).onGloballyPositioned {
                        if (active) onBounds(phrase.id, PhraseDialogAnchor(it.boundsInWindow(), neighbors))
                    }, grip = if (active) Modifier.reorderGrip(phrase.id, drag) else Modifier)
        }
        item(key = "add-phrase") {
            TextButton(onClick = feedbackClick(add), modifier = Modifier.height(QuickPhraseHeight).testTag("add-phrase").onGloballyPositioned {
                if (active) onAddBounds(PhraseDialogAnchor(it.boundsInWindow()))
            },
                shapes = expressivePillShapes(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text("添加快捷文本", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (active) ReorderAutoScroll(drag, { grid.layoutInfo.viewportSize.height.toFloat() }, { grid.scrollBy(it) })
}
