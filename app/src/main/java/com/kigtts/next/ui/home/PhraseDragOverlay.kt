@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import com.kigtts.next.ui.components.reorder.*

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.delay

/** Edge dwell belongs to the stable pane, so switching the grid cannot cancel the live pointer. */
@Composable
fun BoxScope.PhraseDragOverlay(drag: ReorderDrag, phrases: List<QuickPhrase>, category: String, categories: List<String>,
    width: Float, model: AppViewModel, columns: Int = 2) {
    val edge = with(LocalDensity.current) { 16.dp.toPx() }
    val direction by remember(drag, width, edge) { derivedStateOf {
        if (drag.key == null || drag.settling) 0 else if (drag.pointer.x < edge) -1 else if (drag.pointer.x > width - edge) 1 else 0
    } }
    val target = categories.getOrNull(categories.indexOf(category) + direction).takeIf { direction != 0 }
    val latestCategory by rememberUpdatedState(category)
    val latestCategories by rememberUpdatedState(categories)
    var armed by remember { mutableStateOf(true) }
    val switched = feedbackClick {}
    val latestFeedback by rememberUpdatedState(switched)
    LaunchedEffect(drag.key, direction, drag.settling) {
        if (drag.key == null || drag.settling || direction == 0) { armed = true; return@LaunchedEffect }
        if (!armed) return@LaunchedEffect
        delay(550)
        val destination = latestCategories.getOrNull(latestCategories.indexOf(latestCategory) + direction)
        val id = drag.key
        if (id != null && destination != null && model.quickTexts.transferPhrase(id, destination)) {
            armed = false; latestFeedback(); model.category(destination)
        }
    }
    AnimatedVisibility(target != null && drag.key != null && armed, modifier = Modifier.align(if (direction < 0) Alignment.CenterStart else Alignment.CenterEnd),
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()), exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
        Box(Modifier.width(24.dp).fillMaxHeight().testTag("phrase-edge-hint")
            .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
            Icon(if (direction < 0) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                "移到分组 " + target.orEmpty(), tint = MaterialTheme.colorScheme.primary)
        }
    }
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
                }.testTag("dragged-phrase").clearAndSetSemantics {}, elevation = elevation)
    }
}
