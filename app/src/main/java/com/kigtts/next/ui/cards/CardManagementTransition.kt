@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Retain browser state while the same photo travels to or from its management slot. */
@Composable
internal fun CardManagementTransition(managing: Boolean, browser: @Composable () -> Unit, manager: @Composable () -> Unit) {
    androidx.compose.animation.SharedTransitionLayout {
    val shared = this
    val geometry = remember { CardManagementGeometry() }
    val progress = remember { Animatable(if (managing) 1f else 0f) }
    val reveal = remember(progress) { derivedStateOf { progress.value } }
    val managerVisible by remember(managing, progress) { derivedStateOf { managing || progress.value > .001f } }
    val managerInteractive by remember(managing, progress, shared) { derivedStateOf { managing && progress.value >= .999f && !shared.isTransitionActive } }
    var retainedManager by remember { mutableStateOf(manager) }
    SideEffect { if (managing) retainedManager = manager }
    LaunchedEffect(managing) {
        if (managing && geometry.geometry == null) {
            // Endpoint layout has time to publish the selected small paper before any morph starts.
            withFrameNanos { }; withFrameNanos { }
            geometry.capture()
        }
        progress.animateTo(if (managing) 1f else 0f, tween(300, easing = FastOutSlowInEasing))
        if (!managing) geometry.reset()
    }
    CompositionLocalProvider(LocalPolaroidSharedScope provides this, LocalCardManagementGeometry provides geometry, LocalCardManagementReveal provides reveal,
        LocalCardManagementActive provides managerInteractive) {
        Box(Modifier.fillMaxSize().testTag("card-management-transition")) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { geometry.browser = it }
                .graphicsLayer {
                    val phase = reveal.value
                    // A reused graphics layer must not inherit the last paper's transform.
                    // Reset the neutral endpoint even before geometry is available after recreation.
                    scaleX = 1f; scaleY = 1f
                    translationX = 0f; translationY = 0f
                    clip = false
                    alpha = 1f - phase
                }.then(if (managing) Modifier.clearAndSetSemantics { } else Modifier)) {
                    CompositionLocalProvider(LocalPolaroidPhotoVisible provides !managing) { browser() }
                }
            if (managerVisible || isTransitionActive) Box(Modifier.fillMaxSize()
                .graphicsLayer { alpha = reveal.value }
                .then(if (!managing) Modifier.clearAndSetSemantics { } else Modifier)) {
                    CompositionLocalProvider(LocalPolaroidPhotoVisible provides managing) { retainedManager() }
                }
        }
    }
    }
}
