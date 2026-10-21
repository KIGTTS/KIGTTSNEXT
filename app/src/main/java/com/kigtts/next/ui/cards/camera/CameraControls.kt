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
    countingDown: Boolean = false, cancelCountdown: () -> Unit = {}, showCancel: Boolean = true,
    gallery: (() -> Unit)? = null, systemCamera: (() -> Unit)? = null) {
    val tonal = compactTonalIconColors().let { if (!active) it.keepColorsWhenLocked() else it }
    Box(if (vertical) Modifier.width(92.dp).fillMaxHeight().testTag("camera-side-controls")
        else Modifier.fillMaxWidth().height(92.dp).testTag("camera-bottom-controls")) {
        if (showCancel || gallery != null) ToolButton(if (gallery != null) "选择照片" else "取消拍摄",
            if (gallery != null) Icons.Default.PhotoLibrary else Icons.Default.Close, gallery ?: cancel,
            Modifier.align(if (vertical) Alignment.BottomCenter else Alignment.CenterStart).padding(12.dp)
                .testTag(if (gallery != null) "camera-gallery" else "camera-cancel"),
            size = 44.dp, shapes = IconButtonDefaults.shapes(RoundedCornerShape(22.dp), RoundedCornerShape(10.dp)),
            colors = tonal,
            enabled = active && if (gallery != null) !locked else !busy,
            onLongClick = if (gallery != null) systemCamera else null)
        ToolButton(if (countingDown) "取消倒计时" else "拍照", when {
            countingDown -> Icons.Default.TimerOff
            busy -> Icons.Default.HourglassEmpty
            else -> Icons.Default.CameraAlt
        }, if (countingDown) cancelCountdown else capture,
            Modifier.align(Alignment.Center).testTag("camera-shutter"), size = 76.dp,
            shapes = IconButtonDefaults.shapes(RoundedCornerShape(38.dp), RoundedCornerShape(20.dp)),
            colors = IconButtonDefaults.filledIconButtonColors().let { if (!active) it.keepColorsWhenLocked() else it }, enabled = active && !busy && (countingDown || !locked && canCapture))
        ToolButton("切换镜头", Icons.Default.Cameraswitch, flip,
            Modifier.align(if (vertical) Alignment.TopCenter else Alignment.CenterEnd).padding(12.dp).testTag("camera-flip"),
            size = 44.dp, shapes = IconButtonDefaults.shapes(RoundedCornerShape(22.dp), RoundedCornerShape(10.dp)),
            colors = tonal,
            enabled = active && !locked && canFlip)
    }
}
