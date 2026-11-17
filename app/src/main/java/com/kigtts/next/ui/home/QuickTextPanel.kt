@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.foundation.interaction.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.components.reorder.*

@Composable
fun QuickTextPanel(state: AppState, phrases: List<QuickPhrase>, categories: List<String>, model: AppViewModel, motion: HomeSheetMotion, modifier: Modifier = Modifier) {
    val singleRow by remember(motion) { derivedStateOf { motion.height <= 192f } }
    BoxWithConstraints(modifier) {
        QuickTextContent(state, phrases, categories, model, singleRow, quickPhraseColumns(maxWidth.value - 16f), Modifier.fillMaxSize(),
            categoryOpacity = { ((motion.height - 96f) / (HomePanelSizes.PEEK - 96f)).coerceIn(0f, 1f) })
    }
}

@Composable
fun QuickTextContent(state: AppState, phrases: List<QuickPhrase>, categories: List<String>, model: AppViewModel,
    singleRow: Boolean, columns: Int, modifier: Modifier = Modifier, categoryOpacity: () -> Float = { 1f }) {
    val feedback = rememberDragFeedback()
    val drag = rememberReorderDrag({ emptyList() }, horizontalOnly = singleRow, animateDrop = true,
        feedback = feedback, move = model.quickTexts::movePhrase)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val layouts = rememberSaveableStateHolder()
    var menu by remember { mutableStateOf<QuickTextMenuTarget?>(null) }
    var editGroup by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier.padding(horizontal = 8.dp).testTag("quick-sort-pane").reorderGestures(drag,
        onStart = model.quickSort::begin, onStationaryHold = { menu = it.menuTarget() })) {
        Column(Modifier.fillMaxSize()) {
            // The pager changes axis with the layout. Keep the fade outside that keyed
            // pager so rebuilding it cannot discard the outgoing list before it fades.
            AnimatedContent(singleRow, Modifier.weight(1f).fillMaxWidth(), label = "quick-text-layout-mode",
                transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(140))).using(null) }) { isRow ->
                layouts.SaveableStateProvider(isRow) {
                    CategoryContentMotion(state.selectedCategory, categories, drag.key == null && isRow == singleRow,
                        model::category, vertical = isRow) { category, active ->
                        QuickPhraseLayout(phrases.filter { it.category == category }, isRow, columns, model, drag,
                            active && isRow == singleRow)
                    }
                }
            }
            CategoryTabs(state, categories, model, Modifier.graphicsLayer { alpha = categoryOpacity() })
        }
        PhraseEdgeTransfer(drag, state.selectedCategory, categories, with(density) { maxWidth.toPx() }, model, enabled = !singleRow)
        QuickPhraseDragOverlay(drag, phrases, singleRow, columns, model)
    }
    QuickTextContextMenu(menu, model, { menu = null }, { editGroup = it })
    editGroup?.let { EditCategoryDialog(it, categories, model) { editGroup = null } }
}

@Composable
internal fun PhraseTile(phrase: QuickPhrase, select: () -> Unit, neighbors: CornerNeighbors,
    pressed: Boolean, neighborPressed: Boolean, source: MutableInteractionSource, modifier: Modifier,
    repository: com.kigtts.next.media.MediaRepository, hidden: Boolean = false, elevation: androidx.compose.ui.unit.Dp = 0.dp) {
    val shape = homeCardShape(neighbors, pressed, neighborPressed)
    // A local shadow follows the translated floating card. Platform elevation projects
    // against the window light and can separate from a card lifted from the final row.
    val lifted = if (elevation > 0.dp) Modifier.dropShadow(shape,
        Shadow(radius = 12.dp, color = Color.Black.copy(alpha = .2f * (elevation.value / 8f).coerceIn(0f, 1f)),
            offset = DpOffset(0.dp, 4.dp))) else Modifier
    Surface(onClick = feedbackClick(select), interactionSource = source,
        modifier = modifier.then(lifted).homePreviewRegion("phrase/" + phrase.id).then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier.testTag("quick-phrase-" + phrase.id)),
        color = quickPhraseColor(phrase), contentColor = quickPhraseContentColor(phrase), shape = shape) {
        Box(Modifier.fillMaxSize()) {
        com.kigtts.next.ui.media.PhraseMediaBackdrop(phrase, repository, Modifier.matchParentSize(), quickPhraseColor(phrase))
        QuickPhraseText(phrase, Modifier.fillMaxHeight().fillMaxWidth(if (phrase.mediaId == null) 1f else .60f)
            .padding(start = 12.dp, end = if (phrase.mediaId == null) 12.dp else 4.dp, top = 12.dp, bottom = 12.dp))
        }
    }
}
