@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*

@Composable
internal fun CameraFilters(filter: PhotoFilter, enabled: Boolean, select: (PhotoFilter) -> Unit, vertical: Boolean = false,
    strength: Float = 1f, changeStrength: (Float) -> Unit = {}) {
    val item: @Composable (PhotoFilter) -> Unit = { option -> key(option) {
        val press = rememberImmediateButtonPress(remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
        LaunchedEffect(enabled) { if (!enabled) press.cancel() }
        FilledTonalButton(onClick = feedbackClick { press.click(); select(option) },
            modifier = Modifier.testTag("camera-filter/" + option.name)
                .then(if (enabled) Modifier.immediateButtonPress(press) else Modifier)
                .semantics { selected = option == filter },
            enabled = enabled, interactionSource = press.native, shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (option == filter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (option == filter) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant).animateEnabled(enabled)) {
            Text(option.label, maxLines = 1)
        }
    } }
    Column(Modifier.then(if (vertical) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxWidth())
        .padding(6.dp).testTag("camera-filters"), horizontalAlignment = Alignment.CenterHorizontally) {
        if (vertical) Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PhotoFilter.entries.forEach { item(it) }
        } else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PhotoFilter.entries.forEach { item(it) }
        }
        AnimatedVisibility(filter != PhotoFilter.ORIGINAL, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text("强度 ${(strength.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(strength.coerceIn(0f, 1f), changeStrength, Modifier.fillMaxWidth().testTag("camera-filter-strength"),
                    enabled = enabled, valueRange = 0f..1f)
            }
        }
    }
}

@Composable
internal fun CameraError(session: PolaroidCameraSession, active: Boolean, retry: () -> Unit) {
    AnimatedVisibility(session.error != null, enter = fadeIn(), exit = fadeOut()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(session.error.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            MotionTextButton(feedbackClick(retry), enabled = active && !session.busy && !session.countingDown) { Text("重试") }
        }
    }
}

/** Independent alignment keeps the shutter centered regardless of either auxiliary action's width. */
@Composable
internal fun CameraActionBar(busy: Boolean, active: Boolean, canCapture: Boolean, canFlip: Boolean,
    cancel: () -> Unit, capture: () -> Unit, flip: () -> Unit, vertical: Boolean, locked: Boolean = busy,
    countingDown: Boolean = false, cancelCountdown: () -> Unit = {}) {
    Box(if (vertical) Modifier.width(92.dp).fillMaxHeight().testTag("camera-side-controls")
        else Modifier.fillMaxWidth().height(92.dp).testTag("camera-bottom-controls")) {
        ToolButton("取消拍摄", Icons.Default.Close, cancel,
            Modifier.align(if (vertical) Alignment.TopCenter else Alignment.CenterStart).padding(12.dp).testTag("camera-cancel"),
            size = 44.dp, shapes = IconButtonDefaults.shapes(RoundedCornerShape(22.dp), RoundedCornerShape(10.dp)),
            enabled = active && !busy)
        ToolButton(if (countingDown) "取消倒计时" else "拍照", when {
            countingDown -> Icons.Default.TimerOff
            busy -> Icons.Default.HourglassEmpty
            else -> Icons.Default.CameraAlt
        }, if (countingDown) cancelCountdown else capture,
            Modifier.align(Alignment.Center).testTag("camera-shutter"), size = 76.dp,
            shapes = IconButtonDefaults.shapes(RoundedCornerShape(38.dp), RoundedCornerShape(20.dp)),
            colors = IconButtonDefaults.filledIconButtonColors(), enabled = active && !busy && (countingDown || !locked && canCapture))
        ToolButton("切换镜头", Icons.Default.Cameraswitch, flip,
            Modifier.align(if (vertical) Alignment.BottomCenter else Alignment.CenterEnd).padding(12.dp).testTag("camera-flip"),
            size = 44.dp, shapes = IconButtonDefaults.shapes(RoundedCornerShape(22.dp), RoundedCornerShape(10.dp)),
            enabled = active && !locked && canFlip)
    }
}
