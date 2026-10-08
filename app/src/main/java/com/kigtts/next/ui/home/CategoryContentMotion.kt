@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun CategoryContentMotion(category: String, categories: List<String>, swipeEnabled: Boolean, select: (String) -> Unit,
    modifier: Modifier = Modifier, content: @Composable (String, Boolean) -> Unit) {
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val latestCategories by rememberUpdatedState(categories)
    val latestCategory by rememberUpdatedState(category)
    val latestSelect by rememberUpdatedState(select)
    val latestContent by rememberUpdatedState(content)
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val sections = rememberSaveableStateHolder()
    AnimatedContent(category, modifier.fillMaxSize().clipToBounds().testTag("category-content-transition")
        .pointerInput(swipeEnabled, threshold) {
            if (!swipeEnabled) return@pointerInput
            var distance = 0f
            detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                val direction = if (distance < -threshold) 1 else if (distance > threshold) -1 else 0
                val index = latestCategories.indexOf(latestCategory)
                if (direction != 0 && index >= 0) latestCategories.getOrNull(index + direction)?.let(latestSelect)
            }, onDragCancel = { distance = 0f }) { change, amount -> change.consume(); distance += amount }
        }, label = "category-page", transitionSpec = {
        val direction = (latestCategories.indexOf(targetState) - latestCategories.indexOf(initialState)).compareTo(0)
        (fadeIn(effects) + slideInHorizontally(spatial) { direction * it })
            .togetherWith(fadeOut(effects) + slideOutHorizontally(spatial) { -direction * it }).using(null)
    }) { target ->
        Box(Modifier.fillMaxSize().then(if (target == latestCategory) Modifier else Modifier.clearAndSetSemantics {})) {
            sections.SaveableStateProvider(target) { latestContent(target, target == latestCategory) }
        }
    }
}
