package com.kigtts.next.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*

@Composable
fun ResizableTopPanel(
    height: Float, maximum: Float, resize: (Float) -> Unit, modifier: Modifier = Modifier,
    singleLine: Boolean = false, availableHeight: () -> Float = { maximum },
    controls: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current.density
    val latestHeight by rememberUpdatedState(height)
    val latestResize by rememberUpdatedState(resize)
    val dragging = remember { mutableStateOf(false) }
    val pressed = remember { mutableStateOf(false) }
    val localHeight = remember { mutableFloatStateOf(height) }
    val feedback = rememberDragFeedback()
    val latestFeedback by rememberUpdatedState(feedback)
    val minimum = if (singleLine) LISTENING_COMPACT_HEIGHT else 112f
    val current = remember(maximum, singleLine, availableHeight) { derivedStateOf {
        if (singleLine) LISTENING_COMPACT_HEIGHT else
            (if (dragging.value) localHeight.floatValue else latestHeight).coerceIn(112f, minOf(maximum, availableHeight().coerceAtLeast(112f)))
    } }
    val latestCurrent by rememberUpdatedState(current)
    val gestures = remember(maximum, minimum, density) { Modifier.pointerInput(Unit) {
        topHandleGestures(
            press = { pressed.value = true; latestFeedback.start(latestCurrent.value) },
            release = { pressed.value = false; latestFeedback.finish(cancelled = true) },
            start = { localHeight.floatValue = latestCurrent.value; dragging.value = true },
            end = { latestResize(localHeight.floatValue); dragging.value = false; latestFeedback.finish() },
            cancel = { dragging.value = false; latestFeedback.finish(cancelled = true) },
            move = { delta ->
                localHeight.floatValue = (localHeight.floatValue + delta / density).coerceIn(minimum, maximum)
                latestFeedback.move(localHeight.floatValue)
            })
    } }
    Column(modifier) {
        Surface(Modifier.fillMaxWidth().heightFrom { current.value }.testTag("listening-card"), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = homeCardShape(CornerNeighbors(below = true))) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().clipToBounds(), content = content)
                if (!singleLine) controls()
            }
        }
        Box(Modifier.fillMaxWidth().height(LISTENING_HANDLE_GAP.dp).testTag("top-panel-resize").semantics {
            contentDescription = "调整聆听面板高度"
            progressBarRangeInfo = ProgressBarRangeInfo(current.value, minimum..maximum)
            setProgress { latestResize(it.coerceIn(minimum, maximum)); true }
        }.then(gestures), contentAlignment = Alignment.Center) {
            PanelDragIndicator(pressed.value || dragging.value, "listening-drag-indicator")
        }
    }
}
