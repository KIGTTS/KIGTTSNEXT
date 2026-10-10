@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var top by remember { mutableIntStateOf(0) }; var bottom by remember { mutableIntStateOf(0) }
    val horizontal = with(LocalDensity.current) { 12.dp.roundToPx() }
    Surface(modifier, shape = homeCardShape(neighbors),
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box {
            CompositionLocalProvider(com.kigtts.next.ui.media.LocalMediaSafeArea provides
                com.kigtts.next.ui.media.MediaSafeArea(top, horizontal, bottom, horizontal)) {
                MediaTransition(media,state,model,Modifier.fillMaxSize())
            }
            Column {
                Box(Modifier.onSizeChanged { top = it.height }.padding(start = 8.dp, top = 8.dp, end = 8.dp)) { EditorToolbar(state, model) }
                SubtitleBody(state, model, Modifier.weight(1f).fillMaxWidth())
                if (state.audioId != null) com.kigtts.next.ui.media.AudioCardControls(model.cardAudio, Modifier.fillMaxWidth().padding(bottom = 4.dp))
                EditorFooter(state, model, Modifier.onSizeChanged { bottom = it.height })
            }
        }
    }
}

@Composable
private fun EditorFooter(state: AppState, model: AppViewModel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 8.dp).testTag("editor-footer"), verticalAlignment = Alignment.CenterVertically) {
        val contentOpen = state.mediaId != null || state.audioId != null
        ElasticButtonGroup(listOf(ElasticAction("editor-clear", if(contentOpen)"关闭" else "清空", if(contentOpen)Icons.Default.Close else Icons.Default.DeleteSweep,
            { if(contentOpen)model.content.closeContent() else { model.content.clearHistory();model.text("") } }, iconOnly = false)) + cornerActions(UiSlot.EDITOR_BOTTOM_START, model), Modifier.weight(if (state.editing) .25f else .4f))
        if (state.editing) CursorTrack(model, Modifier.weight(.5f))
        else Spacer(Modifier.weight(.2f).testTag("editor-bottom-reserved"))
        Box(Modifier.weight(if (state.editing) .25f else .4f), contentAlignment = Alignment.CenterEnd) {
            val primary = if (state.mediaId != null) ElasticAction("editor-fullscreen", "全屏", Icons.Default.Fullscreen, model::enterSubtitleFullscreen, iconOnly = false)
                else ElasticAction("editor-read", if (state.speaking) "停止" else "朗读",
                    if (state.speaking) Icons.Default.Stop else Icons.Default.PlayArrow, model::speak, iconOnly = false)
            ElasticButtonGroup(listOf(primary) + cornerActions(UiSlot.EDITOR_BOTTOM_END, model), alignEnd = true,
                overflowTag = "editor-bottom-end-more", overflowLabel = "字幕右下更多操作")
        }
    }
}
