@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.home

import com.kigtts.next.i18n.rememberUiCopy

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.UiSlot
import com.kigtts.next.ui.media.MediaTransition
import com.kigtts.next.theme.themeFill

@Composable
fun TextEditor(
    state: AppState, model: AppViewModel, captionsAbove: Boolean, panelBelow: Boolean,
    modifier: Modifier = Modifier,
    neighbors: CornerNeighbors = CornerNeighbors(above = captionsAbove, below = panelBelow),
) {
    val media=model.mediaRepository.get(state.mediaId)
    val renderer by model.subtitleRenderer.selection.collectAsState()
    val webPresentation by model.webPresentation.collectAsState()
    val webFade = webPresentation?.takeIf { it.matches(state) }?.fadeControlsWhenIdle == true
    val rendererFade = renderer?.presentation?.fadeControlsWhenIdle == true && media == null
    val (idle, controlsAlpha) = rememberPanelIdleChrome(webFade || rendererFade,
        state.editing || webFade && (state.mediaEditing || state.voiceInputActive || (LocalModalWindows.current?.count ?: 0) > 0),
        reducedMotion = state.subtitleAppearance.reducedMotion)
    var top by remember { mutableIntStateOf(0) }; var bottom by remember { mutableIntStateOf(0) }
    val sliderAdjusting = remember { mutableStateOf(false) }
    val pinchAdjusting = remember { mutableStateOf(false) }
    val horizontal = with(LocalDensity.current) { (if (renderer != null) 2.dp else 12.dp).roundToPx() }
    val backgroundAlpha = if (state.theme.background == null) 1f else .88f
    val square = state.fullscreen || state.editing || state.mediaEditing
    val contentColor = contentColorFor(subtitleCardColor(state.theme.background != null))
    Box(modifier.testTag("subtitle-card").homePreviewRegion("subtitle").panelIdleWake(idle)
        .subtitleCardCorners(neighbors, square), propagateMinConstraints = true) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(Modifier.themeFill { it.surfaceContainerHigh.copy(alpha = backgroundAlpha) }) {
                val safeArea = com.kigtts.next.ui.media.MediaSafeArea(top, horizontal, bottom, horizontal)
                SubtitleRendererBackdrop(state, model, safeArea, Modifier.matchParentSize())
                CompositionLocalProvider(com.kigtts.next.ui.media.LocalMediaSafeArea provides safeArea) {
                    MediaTransition(media, state, model, Modifier.fillMaxSize())
                }
                Column {
                    Box(Modifier.graphicsLayer { alpha = controlsAlpha.value }.onSizeChanged { top = it.height }
                        .padding(start = subtitleControlsInset, top = subtitleControlsInset, end = subtitleControlsInset)) {
                        EditorToolbar(state, model, onFontAdjustment = { sliderAdjusting.value = it })
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        ProvideIconTextContextMenu(suspended = sliderAdjusting.value || pinchAdjusting.value, keepInEditor = true) {
                            SubtitleBody(state, model, Modifier.fillMaxSize(), rendererInBackground = true,
                                onFontAdjustment = { pinchAdjusting.value = it })
                        }
                    }
                    if (state.audioId != null) com.kigtts.next.ui.media.AudioCardControls(model.cardAudio, Modifier.fillMaxWidth().padding(bottom = 4.dp))
                    EditorFooter(state, model, Modifier.graphicsLayer { alpha = controlsAlpha.value }.onSizeChanged { bottom = it.height })
                }
            }
        }
    }
}

@Composable
private fun Modifier.subtitleCardCorners(neighbors: CornerNeighbors, square: Boolean): Modifier {
    val radius = LocalCornerRadius.current.coerceIn(4.dp, 20.dp)
    val innerRadius = (LocalCornerRadius.current * .22f).coerceIn(4.dp, radius)
    @Composable fun corner(connected: Boolean, label: String) =
        animateDpAsState(if (square) 0.dp else if (connected) innerRadius else radius,
            MaterialTheme.motionScheme.fastSpatialSpec(), label = label)
    val topStart = corner(neighbors.start || neighbors.above, "subtitle-corner-top-start")
    val topEnd = corner(neighbors.end || neighbors.above, "subtitle-corner-top-end")
    val bottomEnd = corner(neighbors.end || neighbors.below, "subtitle-corner-bottom-end")
    val bottomStart = corner(neighbors.start || neighbors.below, "subtitle-corner-bottom-start")
    // Android 10 can retain a stale hardware-layer clip after the listening panel resizes this card.
    return drawWithCache {
        val shape = RoundedCornerShape(topStart.value.coerceIn(0.dp, 20.dp), topEnd.value.coerceIn(0.dp, 20.dp),
            bottomEnd.value.coerceIn(0.dp, 20.dp), bottomStart.value.coerceIn(0.dp, 20.dp))
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        onDrawWithContent { clipPath(outline) { this@onDrawWithContent.drawContent() } }
    }
}

@Composable
private fun EditorFooter(state: AppState, model: AppViewModel, modifier: Modifier = Modifier) {
    val copy = rememberUiCopy()
    val readVisible by model.synthesisGuidance.visible.collectAsState()
    val reveal = animateFloatAsState(if (state.editing) 1f else 0f,
        tween(220, easing = FastOutSlowInEasing), label = "cursor-slot-reveal")
    Column(modifier.fillMaxWidth().padding(horizontal = subtitleControlsInset).padding(bottom = subtitleControlsInset).testTag("editor-footer")) {
      EditorFooterSlots(reveal, Modifier.fillMaxWidth().testTag("editor-footer-actions"), start = {
        val contentOpen = state.mediaId != null || state.audioId != null
        ElasticButtonGroup(listOf(ElasticAction("editor-clear", if(contentOpen)copy("action.close") else copy("action.clear"), if(contentOpen)Icons.Default.Close else Icons.Default.DeleteSweep,
            { if(contentOpen)model.content.closeContent() else { model.content.clearHistory();model.text("") } }, iconOnly = false)) + cornerActions(UiSlot.EDITOR_BOTTOM_START, model))
      }, middle = {
        CursorTrackSlot(state.editing, model, reveal, Modifier.fillMaxWidth())
      }, end = {
            val expanded = state.subtitleExpanded || state.forcedFullscreen
            val primary = if (state.mediaId != null) ElasticAction("editor-fullscreen", if (expanded) copy("subtitle.exit_fullscreen") else copy("subtitle.fullscreen"),
                if (expanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                { if (expanded) model.exitSubtitleFullscreen() else model.enterSubtitleFullscreen() }, iconOnly = false)
                else if (readVisible || state.speaking) ElasticAction("editor-read", if (state.speaking) copy("subtitle.stop") else copy("subtitle.read_aloud"),
                    if (state.speaking) Icons.Default.Stop else Icons.Default.PlayArrow, model::speak, iconOnly = false)
                else null
            ElasticButtonGroup(listOfNotNull(primary) + cornerActions(UiSlot.EDITOR_BOTTOM_END, model), alignEnd = true,
                overflowTag = "editor-bottom-end-more", overflowLabel = copy("subtitle.more_bottom_end"), stableItems = true)
      })
    }
}
