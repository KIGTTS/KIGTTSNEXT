@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick

@Composable
internal fun VoiceInputReleaseZones(gesture: VoiceInputGestureState, automaticRead: Boolean,
    cancel: () -> Unit, send: () -> Unit, read: () -> Unit, continuous: () -> Unit) {
    val feedback = LocalHapticFeedback.current
    val selected = gesture.selected
    var previousSelection by remember { mutableStateOf<VoiceReleaseTarget?>(null) }
    LaunchedEffect(selected, gesture.armed) {
        if (gesture.armed) {
            if (previousSelection != null && previousSelection != selected) feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
            previousSelection = selected
        } else previousSelection = null
    }
    Column(Modifier.fillMaxWidth().testTag("voice-input-release-zones")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!automaticRead) ReleaseZone("发送并朗读", VoiceReleaseTarget.READ, Icons.AutoMirrored.Filled.VolumeUp,
                gesture, selected, read, Modifier.weight(1f), -9f)
            ReleaseZone("取消", VoiceReleaseTarget.CANCEL, Icons.Default.Close,
                gesture, selected, cancel, Modifier.weight(1f), if (automaticRead) -9f else 0f)
            ReleaseZone("持续识别", VoiceReleaseTarget.CONTINUOUS, Icons.Default.Hearing,
                gesture, selected, continuous, Modifier.weight(1f), 9f)
        }
        val highlight = selected == VoiceReleaseTarget.SEND
        val color by animateColorAsState(if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .94f)
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .80f), MaterialTheme.motionScheme.fastEffectsSpec(), label = "voice-send-color")
        Box(Modifier.fillMaxWidth().height(140.dp).onGloballyPositioned { gesture.area(VoiceReleaseTarget.SEND, it.boundsInRoot()) }
            .clip(SendArcShape).background(color).clickable(onClick = feedbackClick(send)).testTag("voice-input-send"),
            contentAlignment = Alignment.TopCenter) {
            Row(Modifier.padding(top = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(if (gesture.armed) "松开 发送" else "发送", color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ReleaseZone(label: String, target: VoiceReleaseTarget, icon: androidx.compose.ui.graphics.vector.ImageVector,
    gesture: VoiceInputGestureState, selected: VoiceReleaseTarget, action: () -> Unit, modifier: Modifier, rotation: Float) {
    val active = selected == target
    val scale by animateFloatAsState(if (active) 1.045f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "voice-zone-size")
    val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .98f)
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .82f), MaterialTheme.motionScheme.fastEffectsSpec(), label = "voice-zone-color")
    DisposableEffect(target, gesture) { onDispose { gesture.removeArea(target) } }
    Row(modifier.height(80.dp).onGloballyPositioned { gesture.area(target, it.boundsInRoot()) }
        .graphicsLayer { rotationZ = rotation; scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(50)).background(color).clickable(onClick = feedbackClick(action))
        .testTag("voice-input-" + target.name.lowercase()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

private object SendArcShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Generic(Path().apply {
        moveTo(0f, size.height * .28f)
        quadraticTo(size.width * .5f, -size.height * .18f, size.width, size.height * .28f)
        lineTo(size.width, size.height); lineTo(0f, size.height); close()
    })
}
