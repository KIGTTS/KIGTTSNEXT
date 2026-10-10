@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*

@Composable
fun QuickTextPanel(state: AppState, phrases: List<QuickPhrase>, categories: List<String>, model: AppViewModel, motion: HomeSheetMotion, modifier: Modifier = Modifier) {
    val singleRow by remember(motion) { derivedStateOf { motion.height <= 192f } }
    QuickTextContent(state, phrases, categories, model, singleRow, 2, modifier)
}

@Composable
fun QuickTextContent(state: AppState, phrases: List<QuickPhrase>, categories: List<String>, model: AppViewModel,
    singleRow: Boolean, columns: Int, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp)) {
        CategoryContentMotion(state.selectedCategory, categories, !singleRow, model::category, Modifier.weight(1f)) { category, _ ->
            QuickPhraseLayout(phrases.filter { it.category == category }, singleRow, columns, model)
        }
        CategoryTabs(state, categories, model)
    }
}

@Composable
private fun QuickPhraseLayout(filtered: List<QuickPhrase>, singleRow: Boolean, columns: Int, model: AppViewModel) {
    val sources = remember(filtered.map { it.id }) { filtered.map { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }
    val currentRow by rememberUpdatedState(singleRow)
    val currentPhrases by rememberUpdatedState(filtered)
    val currentSources by rememberUpdatedState(sources)
    val currentPressed by rememberUpdatedState(pressed)
    val grid = rememberLazyGridState()
    val row = rememberLazyListState()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    if (filtered.isEmpty()) Box(Modifier.fillMaxSize()) { Text("还没有短语，点击编辑添加。", Modifier.padding(12.dp)) }
    else AnimatedContent(singleRow, modifier = Modifier.fillMaxSize().clipToBounds().testTag("phrases-layout-transition"),
        label = "quick-text-layout", transitionSpec = {
            val direction = if (targetState) -1 else 1
            (fadeIn(effects) + slideInVertically(spatial) { direction * it / 8 })
                .togetherWith(fadeOut(effects) + slideOutVertically(spatial) { -direction * it / 8 }).using(null)
        }) { isRow ->
        val filtered = currentPhrases; val sources = currentSources; val pressed = currentPressed
        val listModifier = Modifier.fillMaxSize().then(if (isRow == currentRow) Modifier else Modifier.clearAndSetSemantics {})
        if (isRow) LazyRow(listModifier.testTag("phrases-horizontal"), state = row, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(filtered, key = { _, phrase -> phrase.id }) { index, phrase ->
                PhraseTile(phrase, { model.content.send(phrase) }, CornerNeighbors(start = index > 0, end = index < filtered.lastIndex),
                    pressed[index], pressed.getOrElse(index - 1) { false } || pressed.getOrElse(index + 1) { false },
                    sources[index], Modifier.width(164.dp).fillParentMaxHeight(), model.mediaRepository)
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
                    Modifier.height(QuickPhraseHeight).animateItem(fadeInSpec = null, fadeOutSpec = null), model.mediaRepository)
            }
        }
    }
}

@Composable
private fun PhraseTile(phrase: QuickPhrase, select: () -> Unit, neighbors: CornerNeighbors,
    pressed: Boolean, neighborPressed: Boolean, source: MutableInteractionSource, modifier: Modifier,
    repository: com.kigtts.next.media.MediaRepository) {
    Surface(onClick = feedbackClick(select), interactionSource = source, modifier = modifier.testTag("quick-phrase-" + phrase.id),
        color = quickPhraseColor(phrase), contentColor = quickPhraseContentColor(phrase), shape = homeCardShape(neighbors, pressed, neighborPressed)) {
        Box(Modifier.fillMaxSize()) {
        com.kigtts.next.ui.media.PhraseMediaBackdrop(phrase.mediaId, repository, Modifier.matchParentSize(), quickPhraseColor(phrase))
        QuickPhraseText(phrase, Modifier.fillMaxHeight().fillMaxWidth(if (phrase.mediaId == null) 1f else .60f)
            .padding(start = 12.dp, end = if (phrase.mediaId == null) 12.dp else 4.dp, top = 12.dp, bottom = 12.dp))
        }
    }
}
