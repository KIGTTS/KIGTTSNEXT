package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kigtts.next.state.*
import com.kigtts.next.ui.media.MediaTransition
import com.kigtts.next.ui.media.PluginSubtitleRenderer
import com.kigtts.plugin.api.PluginGesture
import com.kigtts.next.ui.navigation.excludeHomeSideSwipes

@Composable
fun SubtitleBody(state: AppState, model: AppViewModel, modifier: Modifier = Modifier, rendererInBackground: Boolean = false) {
    val currentState by rememberUpdatedState(state)
    val renderer by model.subtitleRenderer.selection.collectAsState()
    val media = model.mediaRepository.get(state.mediaId)
    val ownsSwipes = !state.editing && (media != null || renderer?.presentation?.blockHomeSideSwipes == true ||
        renderer?.native is com.kigtts.plugin.api.NativeSubtitleRenderer.Led ||
        model.registry.gestureActions(PluginGesture.SUBTITLE_STROKE, model.context()).isNotEmpty())
    val surface = modifier.excludeHomeSideSwipes(ownsSwipes)
    // The full-card WebView is already mounted below this column. An invisible AnimatedContent
    // above AndroidView can own its hit path; leave only a layout spacer for interactive pages.
    if (media?.kind == com.kigtts.next.media.MediaKind.HTML || media?.kind == com.kigtts.next.media.MediaKind.WEB) {
        Spacer(surface)
        return
    }
    MediaTransition(media,state,model,surface.then(if(media!=null)Modifier.testTag("subtitle-content") else Modifier).clipToBounds(),
        renderFullCardMedia=false) {
        SubtitleText(currentState,model,Modifier.fillMaxSize(),rendererInBackground)
    }
}

@Composable
private fun SubtitleText(state:AppState,model:AppViewModel,modifier:Modifier,rendererInBackground:Boolean) {
    val renderer by model.subtitleRenderer.selection.collectAsState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val flipped = state.subtitleFlipped && !state.editing
    val rotation = animateFloatAsState(if (flipped) 180f else 0f, tween(if (state.subtitleAppearance.reducedMotion) 0 else 320,
        easing = FastOutSlowInEasing), label = "subtitle-flip")
    val hintOpacity = animateFloatAsState(if (state.editing) .28f else 1f, tween(180), label = "subtitle-hint-focus")
    val style = com.kigtts.next.theme.LocalAppearanceFont.current.apply(TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = preferredSubtitleFontSize(state.subtitleSizePercent),
        lineHeight = 1.25.em, fontWeight = if (state.subtitleAppearance.bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = state.subtitleAppearance.alignment.textAlignment))
    val density = LocalDensity.current
    val text = state.editorText
    val nativeLedZoom = !state.editing && renderer?.native is com.kigtts.plugin.api.NativeSubtitleRenderer.Led
    LaunchedEffect(state.editing) { if (state.editing) { focus.requestFocus(); keyboard?.show() } }
    CompositionLocalProvider(LocalSubtitleFitCache provides rememberSubtitleFitCache()) {
    BoxWithConstraints(modifier.testTag("subtitle-content").subtitleInputGestures(model, !state.editing).padding(20.dp, 12.dp)
        .semantics { stateDescription = if (flipped) "倒置字幕" else "正向字幕" }
        .ledScrubGesture(model.subtitleRenderer.ledPlayback, nativeLedZoom, state.subtitleAppearance.reducedMotion)
        .subtitleZoom(model to nativeLedZoom, onZoomEnd = {
            if (nativeLedZoom) model.subtitleRenderer.takeLedZoom()?.let(model::setSubtitleSizePercent)
        }) { factor ->
            val current = model.state.value.subtitleSizePercent
            if (nativeLedZoom) model.subtitleRenderer.previewLedZoom(current, factor)
            else model.setSubtitleSizePercent(current * factor)
        }
        .graphicsLayer { rotationZ = rotation.value }, contentAlignment = Alignment.Center) {
        val width = with(density) { maxWidth.roundToPx() }
        val height = with(density) { maxHeight.roundToPx() }
        if (state.editing) {
            SubtitleInput(state, model, style, width, height, focus, hintOpacity)
        } else if (renderer != null) {
            val selection = renderer!!
            Box(Modifier.fillMaxSize()) {
                if (!rendererInBackground) key(if (selection.native == null) selection else selection.plugin) { PluginSubtitleRenderer(selection, text, state.subtitlePlaceholder,
                    state.subtitleSizePercent, Modifier.fillMaxSize(),
                    onQuickSwipe = { model.subtitleRenderer.setQuickSwipe(selection.owner, it) }, onUnavailable = {
                        model.rendererUnavailable(selection.owner)
                    }, reducedMotion = state.subtitleAppearance.reducedMotion,
                    zoomPreview = model.subtitleRenderer::ledZoomPreview, ledPlayback = model.subtitleRenderer.ledPlayback) }
                // Display renderers never own editing/zoom gestures. Keep the native input affordance.
                if (model.registry.gestureActions(PluginGesture.SUBTITLE_STROKE, model.context()).isEmpty())
                    Spacer(Modifier.matchParentSize().testTag("subtitle-renderer-input").subtitleInputGestures(model))
            }
        } else SubtitleDisplay(state, model, text, style, width, height, hintOpacity)
        if (!state.editing && model.registry.gestureActions(PluginGesture.SUBTITLE_STROKE, model.context()).isNotEmpty())
            SubtitleStrokeOverlay(model, Modifier.matchParentSize())
    }
    }
}
