package com.kigtts.next.ui.sketch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.kigtts.next.ui.navigation.LocalPageActive

/** Keep the same drawing mounted behind the album, including the complete return animation. */
@Composable
internal fun SketchGalleryMotion(transition: MutableTransitionState<Boolean>, gallery: @Composable () -> Unit) {
    val active = LocalPageActive.current && transition.targetState
    AnimatedVisibility(transition, Modifier.fillMaxSize().testTag("sketch-gallery-motion"),
        enter = slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it },
        exit = slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it }) {
        CompositionLocalProvider(LocalPageActive provides active) {
            Box(Modifier.fillMaxSize().testTag("sketch-gallery-page")) {
            Box(Modifier.fillMaxSize().then(if (active) Modifier else Modifier.clearAndSetSemantics {})
                .pointerInput(active) {
                    if (!active) awaitPointerEventScope { while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    } }
                }) { gallery() }
            }
        }
    }
}
