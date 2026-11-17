package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
    val floating = if (model.captionServiceReady && controlsOwner == null) listOf(
        ElasticAction("listening-open-window", uiText("listening.open_window"), MaterialSymbols.vector("open_in_new")
            ?: Icons.AutoMirrored.Filled.OpenInNew, model::openCaptionWindow)) else emptyList()
    val right = remember(context, state.pluginRevision, controlsOwner) { cornerActions(UiSlot.LISTENING_BOTTOM_END, model, controlsOwner) }
    LaunchedEffect(controlsOwner) { if (controlsOwner != null) fontBar = false }
    ResizableTopPanel(extent, maximum, resize, Modifier.testTag("captions").panelIdleWake(idle), singleLine, availableHeight,
        horizontal = horizontal,
        color = subtitleCardColor(state.theme.background != null),
        contentPadding = if (custom != null) PaddingValues(0.dp) else PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        controls = {
            Box(Modifier.graphicsLayer { alpha = controlsAlpha.value }) {
            if (controlsOwner != null) Row(Modifier.fillMaxWidth()) {
                ElasticButtonGroup(left, Modifier.weight(1f), stableItems = true)
                ElasticButtonGroup(right, Modifier.weight(1f), alignEnd = true, stableItems = true)
            } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val rightWidth = (maxWidth.value - 36f).coerceIn(32f, 68f).dp
            Row(Modifier.fillMaxWidth().height(32.dp).testTag("listening-controls"), verticalAlignment = Alignment.CenterVertically) {
                FontToolbarSwap(fontBar, uiText("listening.font_size"), Modifier.weight(if (horizontal) 1f else .65f)) { expanded ->
                if (expanded) InlineFontBar(uiText("listening.font_size"), state.captionTextSize, 10f..64f, model::setCaptionTextSize,
                    { fontBar = false }, Modifier.fillMaxWidth())
                else ElasticButtonGroup(floating + left, Modifier.fillMaxWidth(), overflowTag = "listening-start-more", overflowLabel = uiText("listening.more_start"))
                }
                Spacer(if (horizontal) Modifier.width(4.dp) else Modifier.weight(.05f))
                ElasticButtonGroup(listOf(ElasticAction("listening-font-size", uiText("listening.adjust_font"), Icons.Default.ZoomIn,
                    { fontBar = !fontBar })) + right, if (horizontal) Modifier.width(rightWidth) else Modifier.weight(.3f), alignEnd = true,
                    overflowTag = "listening-end-more", overflowLabel = uiText("listening.more_end"))
            }
            }
            }
            }
        }) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (custom?.replaceContent != true) ListeningCaptionContent(state.transcript, state.captionTextSize, singleLine,
                Modifier.fillMaxSize().subtitleZoom(model) { model.setCaptionTextSize(model.state.value.captionTextSize * it) },
                finalOnly = state.recognitionFinalOnly, reducedMotion = state.subtitleAppearance.reducedMotion,
                animateReflow = state.subtitleAppearance.displayReflow)
            custom?.let { selected -> key(selected.owner, selected.pageId, selected.plugin) {
                PluginListeningPanel(selected, state, model, Modifier.fillMaxSize())
            } }
        }
    }
}
