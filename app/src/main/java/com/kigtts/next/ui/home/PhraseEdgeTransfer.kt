@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.reorder.ReorderDrag
import kotlinx.coroutines.delay

/** Shared by editing and quick sorting; never cancels the pointer when the category changes. */
@Composable
internal fun BoxScope.PhraseEdgeTransfer(drag: ReorderDrag, category: String, categories: List<String>,
    width: Float, model: AppViewModel, enabled: Boolean = true) {
    val edge = with(LocalDensity.current) { 16.dp.toPx() }
    val direction by remember(drag, width, edge, enabled) { derivedStateOf {
        if (!enabled || drag.key == null || drag.settling) 0 else if (drag.pointer.x < edge) -1 else if (drag.pointer.x > width - edge) 1 else 0
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
            armed = false; drag.viewportChanged(); latestFeedback(); model.category(destination)
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
}
