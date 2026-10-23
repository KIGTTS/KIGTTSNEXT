package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.UiSlot

@Composable
fun ListeningPanel(state: AppState, model: AppViewModel, maximum: Float, singleLine: Boolean = false,
    availableHeight: () -> Float = { maximum }, horizontal: Boolean = false,
    extent: Float = state.captionHeight, resize: (Float) -> Unit = model::captionHeight) {
    var fontBar by remember { mutableStateOf(false) }
    val custom by model.listeningPanel.selection.collectAsState()
    val (idle, controlsAlpha) = rememberPanelIdleChrome(custom?.fadeControlsWhenIdle == true, fontBar)
    val controlsOwner = custom?.takeIf { it.replaceControls }?.owner
    val context = model.context()
    val left = remember(context, state.pluginRevision, controlsOwner) { cornerActions(UiSlot.LISTENING_BOTTOM_START, model, controlsOwner) }
    val right = remember(context, state.pluginRevision, controlsOwner) { cornerActions(UiSlot.LISTENING_BOTTOM_END, model, controlsOwner) }
    LaunchedEffect(controlsOwner) { if (controlsOwner != null) fontBar = false }
    ResizableTopPanel(extent, maximum, resize, Modifier.testTag("captions").panelIdleWake(idle), singleLine, availableHeight,
        horizontal = horizontal,
        contentPadding = if (custom != null) PaddingValues(0.dp) else PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        controls = {
            Box(Modifier.graphicsLayer { alpha = controlsAlpha.value }) {
            if (controlsOwner != null) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                ElasticButtonGroup(left, Modifier.weight(1f), stableItems = true)
                ElasticButtonGroup(right, Modifier.weight(1f), alignEnd = true, stableItems = true)
            } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val rightWidth = (maxWidth.value - 36f).coerceIn(32f, 68f).dp
            Row(Modifier.fillMaxWidth().height(32.dp).testTag("listening-controls"), verticalAlignment = Alignment.CenterVertically) {
                FontToolbarSwap(fontBar, "聆听字号", Modifier.weight(if (horizontal) 1f else .65f)) { expanded ->
                if (expanded) InlineFontBar("聆听字号", state.captionTextSize, 10f..64f, model::setCaptionTextSize,
                    { fontBar = false }, Modifier.fillMaxWidth())
                else ElasticButtonGroup(left, Modifier.fillMaxWidth(), overflowTag = "listening-start-more", overflowLabel = "聆听左下更多操作")
                }
                Spacer(if (horizontal) Modifier.width(4.dp) else Modifier.weight(.05f))
                ElasticButtonGroup(listOf(ElasticAction("listening-font-size", "调整聆听字号", Icons.Default.ZoomIn,
                    { fontBar = !fontBar })) + right, if (horizontal) Modifier.width(rightWidth) else Modifier.weight(.3f), alignEnd = true,
                    overflowTag = "listening-end-more", overflowLabel = "聆听右下更多操作")
            }
            }
            }
            }
        }) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (custom?.replaceContent != true) ListeningCaptionContent(state.transcript, state.captionTextSize, singleLine,
                Modifier.fillMaxSize().subtitleZoom(model) { model.setCaptionTextSize(model.state.value.captionTextSize * it) })
            custom?.let { selected -> key(selected.owner, selected.pageId, selected.plugin) {
                PluginListeningPanel(selected, state, model, Modifier.fillMaxSize())
            } }
        }
    }
}
