package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/** A stable slot keeps camera actions anchored; opacity/scale never reveal through a size clip. */
@Composable
internal fun CameraOverlayPresence(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible,
            modifier = if (visible) Modifier else Modifier.clearAndSetSemantics {},
            enter = fadeIn(tween(180)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = .9f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150, easing = FastOutSlowInEasing), targetScale = .9f)) {
            content()
        }
    }
}
