@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import com.kigtts.next.ui.components.reorder.*

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*

/** Edge dwell belongs to the stable pane, so switching the grid cannot cancel the live pointer. */
@Composable
fun BoxScope.PhraseDragOverlay(drag: ReorderDrag, phrases: List<QuickPhrase>, category: String, categories: List<String>,
    width: Float, model: AppViewModel, columns: Int = 2) {
    PhraseEdgeTransfer(drag, category, categories, width, model)
    val picked = phrases.firstOrNull { it.id == drag.key }
    val position = rememberReorderDrop(drag)
    val elevation by animateDpAsState(if (picked != null && !drag.settling) 8.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "phrase-drag-elevation")
    val scale by animateFloatAsState(if (picked != null && !drag.settling) 1.015f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "phrase-drag-scale")
    val density = LocalDensity.current
    picked?.let { phrase ->
            val group = phrases.filter { it.category == phrase.category }
            val index = group.indexOfFirst { it.id == phrase.id }
            EditablePhraseCard(phrase, {}, {}, CornerNeighbors(start = index % columns > 0,
                end = index % columns < columns - 1 && index < group.lastIndex, above = index >= columns, below = index + columns < group.size), Modifier
                .width(with(density) { drag.floatingBounds.width.toDp() })
                .graphicsLayer {
                    translationX = position.value.x; translationY = position.value.y
                    scaleX = scale; scaleY = scale
                }.testTag("dragged-phrase").clearAndSetSemantics {}, elevation = elevation, repository = model.mediaRepository)
    }
}
