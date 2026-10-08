@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.rememberDragFeedback

/** A relative cursor touch strip, without a Slider value or thumb. */
@Composable
fun CursorTrack(model: AppViewModel, modifier: Modifier = Modifier) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val feedback = rememberDragFeedback()
    val latestFeedback by rememberUpdatedState(feedback)
    val press = rememberDragHandlePress(feedback, { 0f }, { dragging })
    val offset by animateFloatAsState(dragOffset, MaterialTheme.motionScheme.fastSpatialSpec(), label = "cursor-strip")
    Surface(modifier.padding(horizontal = 8.dp).height(32.dp).testTag("cursor-track")
        .semantics {
            contentDescription = "移动光标"
            customActions = listOf(
                CustomAccessibilityAction("光标左移") { model.moveCursor(-1); true },
                CustomAccessibilityAction("光标右移") { model.moveCursor(1); true })
        }.then(press.modifier).pointerInput(model) {
            var remainder = 0f
            val stepWidth = 12.dp.toPx()
            detectHorizontalDragGestures(
                onDragStart = { remainder = 0f; dragging = true },
                onDragEnd = { dragOffset = 0f; dragging = false; latestFeedback.finish() },
                onDragCancel = { dragOffset = 0f; dragging = false; latestFeedback.finish(cancelled = true) },
            ) { change, amount ->
                change.consume()
                remainder += amount
                val steps = (remainder / stepWidth).toInt()
                if (steps != 0) {
                    val before = model.state.value.editorValue.selection
                    model.moveCursor(steps)
                    remainder -= steps * stepWidth
                    if (model.state.value.editorValue.selection != before) latestFeedback.step()
                }
                dragOffset = (dragOffset + amount).coerceIn(-size.width * .3f, size.width * .3f)
            }
        }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Box(contentAlignment = Alignment.Center) {
            PanelDragIndicator(press.pressed || dragging, "cursor-drag-indicator", Modifier.graphicsLayer { translationX = offset }, baseWidth = 40.dp)
        }
    }
}
