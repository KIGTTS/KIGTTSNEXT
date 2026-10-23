package com.kigtts.next.ui.home

import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kigtts.next.state.AppState
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.media.MediaSafeArea
import com.kigtts.next.ui.media.PluginSubtitleRenderer

/** Display renderers paint the card itself; its native controls remain a separate foreground. */
@Composable
internal fun SubtitleRendererBackdrop(state: AppState, model: AppViewModel, safeArea: MediaSafeArea,
    modifier: Modifier = Modifier) {
    val selection by model.subtitleRenderer.selection.collectAsState()
    if (state.editing || state.mediaId != null) return
    val renderer = selection ?: return
    val flipped = state.subtitleFlipped
    val rotation = animateFloatAsState(if (flipped) 180f else 0f, tween(if (state.subtitleAppearance.reducedMotion) 0 else 320,
        easing = FastOutSlowInEasing), label = "renderer-flip")
    val insets = if (flipped) MediaSafeArea(safeArea.bottom, safeArea.left, safeArea.top, safeArea.right) else safeArea
    key(renderer) {
        PluginSubtitleRenderer(renderer, state.editorText, state.subtitlePlaceholder, state.subtitleSizePercent,
            modifier.graphicsLayer { rotationZ = rotation.value }, safeArea = insets,
            onQuickSwipe = { model.subtitleRenderer.setQuickSwipe(renderer.owner, it) },
            onUnavailable = { model.rendererUnavailable(renderer.owner) })
    }
}
