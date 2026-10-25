@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.UiSlot
import com.kigtts.next.ui.media.MediaTransition

@Composable
fun TextEditor(
    state: AppState, model: AppViewModel, captionsAbove: Boolean, panelBelow: Boolean,
    modifier: Modifier = Modifier,
    neighbors: CornerNeighbors = CornerNeighbors(above = captionsAbove, below = panelBelow),
) {
    val media=model.mediaRepository.get(state.mediaId)
    val renderer by model.subtitleRenderer.selection.collectAsState()
    val (idle, controlsAlpha) = rememberPanelIdleChrome(renderer?.presentation?.fadeControlsWhenIdle == true && media == null, state.editing)
    var top by remember { mutableIntStateOf(0) }; var bottom by remember { mutableIntStateOf(0) }
    val horizontal = with(LocalDensity.current) { (if (renderer != null) 2.dp else 12.dp).roundToPx() }
    Surface(modifier.testTag("subtitle-card").homePreviewRegion("subtitle").panelIdleWake(idle), shape = homeCardShape(neighbors),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (state.theme.background == null) 1f else .88f)) {
        Box {
            SubtitleRendererBackdrop(state, model,
                com.kigtts.next.ui.media.MediaSafeArea(top, horizontal, bottom, horizontal), Modifier.matchParentSize())
            CompositionLocalProvider(com.kigtts.next.ui.media.LocalMediaSafeArea provides
                com.kigtts.next.ui.media.MediaSafeArea(top, horizontal, bottom, horizontal)) {
                MediaTransition(media,state,model,Modifier.fillMaxSize())
            }
            Column {
                Box(Modifier.graphicsLayer { alpha = controlsAlpha.value }.onSizeChanged { top = it.height }.padding(start = 8.dp, top = 8.dp, end = 8.dp)) { EditorToolbar(state, model) }
                SubtitleBody(state, model, Modifier.weight(1f).fillMaxWidth(), rendererInBackground = true)
                if (state.audioId != null) com.kigtts.next.ui.media.AudioCardControls(model.cardAudio, Modifier.fillMaxWidth().padding(bottom = 4.dp))
                EditorFooter(state, model, Modifier.graphicsLayer { alpha = controlsAlpha.value }.onSizeChanged { bottom = it.height })
            }
        }
    }
}

@Composable
private fun EditorFooter(state: AppState, model: AppViewModel, modifier: Modifier = Modifier) {
    val readVisible by model.synthesisGuidance.visible.collectAsState()
    val reveal by animateFloatAsState(if (state.editing) 1f else 0f,
        tween(220, easing = FastOutSlowInEasing), label = "cursor-slot-reveal")
    val progress = reveal.coerceIn(0f, 1f)
    val middle = .2f + .3f * progress
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 8.dp).testTag("editor-footer")) {
      Row(Modifier.fillMaxWidth().testTag("editor-footer-actions"), verticalAlignment = Alignment.CenterVertically) {
        val contentOpen = state.mediaId != null || state.audioId != null
        ElasticButtonGroup(listOf(ElasticAction("editor-clear", if(contentOpen)"关闭" else "清空", if(contentOpen)Icons.Default.Close else Icons.Default.DeleteSweep,
            { if(contentOpen)model.content.closeContent() else { model.content.clearHistory();model.text("") } }, iconOnly = false)) + cornerActions(UiSlot.EDITOR_BOTTOM_START, model), Modifier.weight((1f - middle) / 2))
        CursorTrackSlot(state.editing, model, progress, Modifier.weight(middle))
        Box(Modifier.weight((1f - middle) / 2), contentAlignment = Alignment.CenterEnd) {
            val expanded = state.subtitleExpanded || state.forcedFullscreen
            val primary = if (state.mediaId != null) ElasticAction("editor-fullscreen", if (expanded) "退出全屏" else "全屏",
                if (expanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                { if (expanded) model.exitSubtitleFullscreen() else model.enterSubtitleFullscreen() }, iconOnly = false)
                else if (readVisible || state.speaking) ElasticAction("editor-read", if (state.speaking) "停止" else "朗读",
                    if (state.speaking) Icons.Default.Stop else Icons.Default.PlayArrow, model::speak, iconOnly = false)
                else null
            ElasticButtonGroup(listOfNotNull(primary) + cornerActions(UiSlot.EDITOR_BOTTOM_END, model), alignEnd = true,
                overflowTag = "editor-bottom-end-more", overflowLabel = "字幕右下更多操作", stableItems = true)
        }
    }
    }
}
