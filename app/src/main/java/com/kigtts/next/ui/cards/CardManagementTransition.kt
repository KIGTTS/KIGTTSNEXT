package com.kigtts.next.ui.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Retain both endpoint layouts; opening and closing only change their painted transforms. */
@Composable
internal fun CardManagementTransition(managing: Boolean, browser: @Composable () -> Unit, manager: @Composable () -> Unit) {
    val geometry = remember { CardManagementGeometry() }
    val progress = remember { Animatable(if (managing) 1f else 0f) }
    val reveal = remember(progress) { derivedStateOf { progress.value } }
    val managerVisible by remember(managing, progress) { derivedStateOf { managing || progress.value > .001f } }
    val managerInteractive by remember(managing, progress) { derivedStateOf { managing && progress.value >= .999f } }
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
    CompositionLocalProvider(LocalCardManagementGeometry provides geometry, LocalCardManagementReveal provides reveal,
        LocalCardManagementActive provides managerInteractive) {
        Box(Modifier.fillMaxSize().testTag("card-management-transition")) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { geometry.browser = it }
                .graphicsLayer {
                    val phase = reveal.value
                    val paper = geometry.geometry
                    val bounds = paper?.browser
                    if (paper != null && bounds != null) {
                        val scale = 1f + (paper.target.width / paper.source.width.coerceAtLeast(1f) - 1f) * phase
                        scaleX = scale; scaleY = scale
                        val desired = managementPaperCenter(paper, phase)
                        val transformed = bounds.center + (paper.source.center - bounds.center) * scale
                        val offset = desired - transformed
                        translationX = offset.x; translationY = offset.y
                    }
                    alpha = 1f - phase
                }.then(if (managing) Modifier.clearAndSetSemantics { } else Modifier)) { browser() }
            if (managerVisible) Box(Modifier.fillMaxSize()
                .graphicsLayer { alpha = reveal.value }
                .then(if (!managing) Modifier.clearAndSetSemantics { } else Modifier)) { retainedManager() }
        }
    }
}
