@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
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
import com.kigtts.plugin.api.PolaroidFormat

@Composable
internal fun CameraFilters(filter: PhotoFilter, enabled: Boolean, busy: Boolean, select: (PhotoFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)
        .testTag("camera-filters"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PhotoFilter.entries.forEach { option -> key(option) {
            val press = rememberImmediateButtonPress(remember { androidx.compose.foundation.interaction.MutableInteractionSource() })
            LaunchedEffect(enabled) { if (!enabled) press.cancel() }
            FilledTonalButton(onClick = feedbackClick { press.click(); select(option) },
                modifier = Modifier.testTag("camera-filter/" + option.name).then(if (enabled) Modifier.immediateButtonPress(press) else Modifier)
                    .semantics { selected = option == filter },
                enabled = enabled, interactionSource = press.native, shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (option == filter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (option == filter) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant).animateEnabled(enabled)) {
                Text(option.label, maxLines = 1)
            }
        } }
    }
}

@Composable
internal fun CameraError(session: PolaroidCameraSession, active: Boolean, retry: () -> Unit) {
    AnimatedVisibility(session.error != null, enter = fadeIn(), exit = fadeOut()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(session.error.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            MotionTextButton(feedbackClick(retry), enabled = active && !session.busy) { Text("重试") }
        }
    }
}

@Composable
internal fun CameraActionBar(busy: Boolean, active: Boolean, canCapture: Boolean, canFlip: Boolean,
    format: PolaroidFormat, formats: List<PolaroidFormat>, cancel: () -> Unit, capture: () -> Unit,
    flip: () -> Unit, cycleFormat: () -> Unit, vertical: Boolean, zoom: Float = 1f) {
    val small: @Composable (Boolean) -> Unit = { flipLens ->
        ToolButton(if (flipLens) "切换镜头" else "取消拍摄", if (flipLens) Icons.Default.Cameraswitch else Icons.Default.Close,
            if (flipLens) flip else cancel, Modifier.testTag(if (flipLens) "camera-flip" else "camera-cancel"),
            size = 44.dp, shapes = IconButtonDefaults.shapes(RoundedCornerShape(22.dp), RoundedCornerShape(10.dp)),
            enabled = active && !busy && (!flipLens || canFlip))
    }
    val ratio: @Composable () -> Unit = {
        ToolButton("画幅：" + format.cameraLabel(formats.size == 3) + "，点按切换", when (format) {
            PolaroidFormat.MINI -> Icons.Default.CropPortrait
            PolaroidFormat.WIDE -> Icons.Default.CropLandscape
            else -> Icons.Default.CropSquare
        }, cycleFormat, Modifier.testTag("camera-format"), size = 44.dp,
            shapes = IconButtonDefaults.shapes(RoundedCornerShape(22.dp), RoundedCornerShape(10.dp)), enabled = active && !busy)
    }
    val shutter: @Composable () -> Unit = {
        ToolButton("拍照", if (busy) Icons.Default.HourglassEmpty else Icons.Default.CameraAlt, capture,
            Modifier.testTag("camera-shutter"), size = if (vertical) 68.dp else 76.dp,
            shapes = IconButtonDefaults.shapes(RoundedCornerShape(38.dp), RoundedCornerShape(20.dp)),
            colors = IconButtonDefaults.filledIconButtonColors(), enabled = active && !busy && canCapture)
    }
    if (vertical) Column(Modifier.fillMaxWidth().testTag("camera-side-controls"),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row { small(false); small(true) }; ratio(); shutter()
        Text("%.1f×".format(zoom), style = MaterialTheme.typography.labelSmall)
    } else Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("camera-bottom-controls"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        small(false); ratio(); shutter(); small(true)
    }
}
