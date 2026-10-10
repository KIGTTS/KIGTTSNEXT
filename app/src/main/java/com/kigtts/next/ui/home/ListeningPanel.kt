package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.UiSlot

@Composable
fun ListeningPanel(state: AppState, model: AppViewModel, maximum: Float, singleLine: Boolean = false,
    availableHeight: () -> Float = { maximum }, horizontal: Boolean = false,
    extent: Float = state.captionHeight, resize: (Float) -> Unit = model::captionHeight) {
    var fontBar by remember { mutableStateOf(false) }
    val context = model.context()
    val left = remember(context, state.pluginRevision) { cornerActions(UiSlot.LISTENING_BOTTOM_START, model) }
    val right = remember(context, state.pluginRevision) { cornerActions(UiSlot.LISTENING_BOTTOM_END, model) }
    ResizableTopPanel(extent, maximum, resize, Modifier.testTag("captions"), singleLine, availableHeight,
        horizontal = horizontal,
        controls = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val rightWidth = (maxWidth.value - 36f).coerceIn(32f, 68f).dp
            Row(Modifier.fillMaxWidth().height(32.dp).testTag("listening-controls"), verticalAlignment = Alignment.CenterVertically) {
                if (fontBar) InlineFontBar("聆听字号", state.captionTextSize, 10f..64f, model::setCaptionTextSize,
                    { fontBar = false }, Modifier.weight(if (horizontal) 1f else .65f))
                else ElasticButtonGroup(left, Modifier.weight(if (horizontal) 1f else .65f), overflowTag = "listening-start-more", overflowLabel = "聆听左下更多操作")
                Spacer(if (horizontal) Modifier.width(4.dp) else Modifier.weight(.05f))
                ElasticButtonGroup(listOf(ElasticAction("listening-font-size", "调整聆听字号", Icons.Default.ZoomIn,
                    { fontBar = !fontBar })) + right, if (horizontal) Modifier.width(rightWidth) else Modifier.weight(.3f), alignEnd = true,
                    overflowTag = "listening-end-more", overflowLabel = "聆听右下更多操作")
            }
            }
        }) {
        Text(state.transcript, modifier = Modifier.fillMaxWidth().weight(1f).testTag("listening-content")
            .subtitleZoom(model) { model.setCaptionTextSize(model.state.value.captionTextSize * it) }
            .then(if (singleLine) Modifier else Modifier.verticalScroll(rememberScrollState())),
            fontSize = (if (singleLine) state.captionTextSize.coerceAtMost(18f) else state.captionTextSize).sp,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
