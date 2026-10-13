package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.navigation.excludeCameraPull

/** The exposure control follows the focus point, while its entire touch target stays in the photo. */
@Composable
internal fun CameraFocusOverlay(target: CameraFocusTarget?, index: Int, range: IntRange, enabled: Boolean, change: (Int) -> Unit) {
    var last by remember { mutableStateOf(target) }
    LaunchedEffect(target) { if (target != null) last = target }
    AnimatedVisibility(target != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
        last?.let { focus ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val x = with(density) { focus.x.toDp() }.coerceIn(0.dp, maxWidth)
                val y = with(density) { focus.y.toDp() }.coerceIn(0.dp, maxHeight)
                val circle = minOf(48.dp, maxWidth, maxHeight)
                Canvas(Modifier.offset((x - circle / 2).coerceIn(0.dp, (maxWidth - circle).coerceAtLeast(0.dp)),
                    (y - circle / 2).coerceIn(0.dp, (maxHeight - circle).coerceAtLeast(0.dp))).size(circle).testTag("camera-focus-ring")) {
                    drawCircle(Color(0xFFF9E89A), radius = size.minDimension / 2 - 2.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
                if (range.first < range.last && enabled) {
                    val width = minOf(40.dp, maxWidth)
                    val height = minOf(132.dp, maxHeight).coerceAtLeast(1.dp)
                    val right = x + circle / 2 + 8.dp
                    val handleX = (if (right + width <= maxWidth) right else x - circle / 2 - width - 8.dp)
                        .coerceIn(0.dp, (maxWidth - width).coerceAtLeast(0.dp))
                    val handleY = (y - height / 2).coerceIn(0.dp, (maxHeight - height).coerceAtLeast(0.dp))
                    CameraExposureHandle(index, range, change, Modifier.offset(handleX, handleY).size(width, height))
                }
            }
        }
    }
}

@Composable
private fun CameraExposureHandle(index: Int, range: IntRange, change: (Int) -> Unit, modifier: Modifier) {
    val latestIndex = rememberUpdatedState(index); val update = rememberUpdatedState(change)
    val feedback = rememberUpdatedState(LocalHapticFeedback.current)
    var pressed by remember { mutableStateOf(false) }
    val thickness by animateFloatAsState(if (pressed) 4f else 2f, label = "camera-exposure-track")
    fun commit(value: Int) {
        if (value != latestIndex.value) {
            feedback.value.performHapticFeedback(HapticFeedbackType.SegmentTick)
            update.value(value)
        }
    }
    Box(modifier.excludeCameraPull().background(Color.Black.copy(alpha = .22f), RoundedCornerShape(20.dp))
        .testTag("camera-exposure").semantics {
            contentDescription = "曝光补偿"
            progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), range.first.toFloat()..range.last.toFloat(), (range.last - range.first - 1).coerceAtLeast(0))
            setProgress { commit(it.toInt().coerceIn(range)); true }
        }.pointerInput(range) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false); down.consume()
                pressed = true; feedback.value.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                val inset = 18.dp.toPx()
                fun choose(y: Float) = commit(cameraExposureAt(y - inset, (size.height - 2 * inset).coerceAtLeast(1f), range))
                choose(down.position.y)
                try {
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull { it.id == down.id && it.pressed }?.let { input -> choose(input.position.y); input.consume() }
                    } while (event.changes.any { it.pressed })
                } finally { pressed = false }
            }
        }) {
        Canvas(Modifier.matchParentSize()) {
            val top = 18.dp.toPx(); val bottom = size.height - top
            val fraction = (index - range.first).toFloat() / (range.last - range.first).coerceAtLeast(1)
            drawLine(Color.White.copy(alpha = .65f), Offset(size.width / 2, top), Offset(size.width / 2, bottom), thickness.dp.toPx())
            drawCircle(Color(0xFFF9E89A), radius = 7.dp.toPx(), center = Offset(size.width / 2, bottom - (bottom - top) * fraction))
        }
        Icon(Icons.Default.LightMode, null, Modifier.align(Alignment.TopCenter).size(16.dp), tint = Color.White)
    }
}
