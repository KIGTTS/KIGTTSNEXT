@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics

val LocalActionInteractivity = compositionLocalOf { true }

/** Keep outgoing content until its exit finishes; callbacks/labels update without replacing identity. */
@Composable
fun <T> AnimatedActionSet(actions: List<T>, identity: (T) -> Any, modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart,
    content: @Composable (List<T>) -> Unit) {
    val ids = actions.map(identity)
    val latestIds by rememberUpdatedState(ids)
    val latestActions by rememberUpdatedState(actions)
    val latestContent by rememberUpdatedState(content)
    val parentActive by rememberUpdatedState(LocalActionInteractivity.current)
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    Box(modifier, contentAlignment = alignment) {
    AnimatedContent(ids, contentAlignment = alignment, label = "action-presence", transitionSpec = {
        (fadeIn(effects) + scaleIn(spatial, initialScale = .92f))
            .togetherWith(fadeOut(effects) + scaleOut(spatial, targetScale = .92f))
            .using(SizeTransform(clip = false))
    }) { target ->
        var retained by remember { mutableStateOf(latestActions) }
        val active = target == latestIds
        SideEffect { if (active) retained = latestActions }
        CompositionLocalProvider(LocalActionInteractivity provides (active && parentActive)) {
            Box(if (active) Modifier else Modifier.clearAndSetSemantics {}) { latestContent(if (active) latestActions else retained) }
        }
    }
    }
}

@Composable
fun <T> AnimatedButtonContent(value: T, content: @Composable (T) -> Unit) {
    val latestValue by rememberUpdatedState(value)
    val latestContent by rememberUpdatedState(content)
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(value, label = "button-content", transitionSpec = {
        fadeIn(effects).togetherWith(fadeOut(effects))
            .using(SizeTransform(clip = false))
    }) { target ->
        Box(if (target == latestValue) Modifier else Modifier.clearAndSetSemantics {}) { latestContent(target) }
    }
}
