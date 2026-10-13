@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.navigation.excludeCameraPull
import kotlin.math.roundToInt

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
                    val width = minOf(48.dp, maxWidth)
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
    val update = rememberUpdatedState(change)
    val feedback = rememberUpdatedState(LocalHapticFeedback.current)
    var last by remember(range) { mutableIntStateOf(index) }
    SideEffect { last = index }
    fun commit(value: Int) {
        if (value != last) {
            last = value
            feedback.value.performHapticFeedback(HapticFeedbackType.SegmentTick)
            update.value(value)
        }
    }
    val source = remember { MutableInteractionSource() }
    val state = rememberSliderState(index.toFloat(), steps = (range.last - range.first - 1).coerceAtLeast(0),
        valueRange = range.first.toFloat()..range.last.toFloat())
    state.value = index.toFloat()
    state.onValueChange = { value ->
        val selected = value.roundToInt().coerceIn(range)
        state.value = selected.toFloat(); commit(selected)
    }
    Surface(modifier.excludeCameraPull(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f)) {
        Column(Modifier.fillMaxSize().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LightMode, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
            VerticalSlider(state, Modifier.weight(1f).fillMaxWidth().cameraSliderPressFeedback().testTag("camera-exposure")
                .semantics { contentDescription = "曝光补偿" }, reverseDirection = true, interactionSource = source)
        }
    }
}
