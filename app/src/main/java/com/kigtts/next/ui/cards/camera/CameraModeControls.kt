package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

@Composable internal fun CameraModeControls(mode: CameraMode, change: (CameraMode) -> Unit, enabled: Boolean,
    vertical: Boolean, recording: Boolean, duration: Long, allowVideo: Boolean = true, controls: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val overlay = LocalCameraShutterOverlay.current
    val idleInk = if (overlay) Color.White.copy(alpha = .84f) else colors.onSurfaceVariant
    if (vertical) Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        CameraModeStrip(mode, change, enabled, true, allowVideo)
        controls()
    } else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (recording) Text("${duration / 60000}:${(duration / 1000 % 60).toString().padStart(2, '0')}", color = idleInk, modifier = Modifier.padding(6.dp))
        CameraModeStrip(mode, change, enabled, false, allowVideo)
        controls()
    }
}

@Composable internal fun VideoShutter(recording: Boolean, finishing: Boolean, enabled: Boolean, click: () -> Unit) {
    val overlay = LocalCameraShutterOverlay.current
    val ink = cameraShutterAccent()
    val recordAccent = cameraRecordAccent()
    OutlinedButton(feedbackClick(click), Modifier.size(76.dp).semantics {
        contentDescription = if (finishing) "正在保存视频" else if (recording) "停止录像" else "开始录像"
    }, shape = CircleShape,
        contentPadding = PaddingValues(9.dp), enabled = enabled,
        border = BorderStroke(3.dp, ink.copy(alpha = if (enabled || finishing) 1f else .35f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (overlay) Color.Black.copy(alpha = .38f) else MaterialTheme.colorScheme.surfaceContainer,
        )) {
        if (finishing) CircularProgressIndicator(Modifier.size(30.dp), color = ink, strokeWidth = 2.dp)
        else Box(Modifier.size(30.dp).background(recordAccent, if (recording) RoundedCornerShape(7.dp) else CircleShape))
    }
}

@Composable internal fun ScanShutterIndicator() {
    val ink = if (LocalCameraShutterOverlay.current) Color.White.copy(alpha = .84f) else MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.QrCodeScanner, "自动扫码", Modifier.size(32.dp), tint = ink)
    }
}
