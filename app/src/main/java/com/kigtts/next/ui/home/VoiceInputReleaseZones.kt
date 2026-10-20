@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.vector
import com.kigtts.plugin.api.*

private data class ReleaseItem(val id: String, val label: String, val icon: ImageVector,
    val target: VoiceReleaseTarget?, val action: () -> Unit, val iconOnly: Boolean = false)

@Composable
internal fun VoiceInputReleaseZones(gesture: VoiceInputGestureState, automaticRead: Boolean,
    cancel: () -> Unit, send: () -> Unit, read: () -> Unit, continuous: () -> Unit, landscape: Boolean = false,
    extensions: List<RegisteredAction> = emptyList(), extension: (RegisteredAction) -> Unit = {}) {
    val feedback = LocalHapticFeedback.current
    val selection = gesture.selectedPluginAction ?: gesture.selected.name
    var previous by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selection, gesture.armed) {
        if (gesture.armed) {
            if (previous != null && previous != selection) feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
            previous = selection
        } else previous = null
    }
    val items = buildList {
        if (!automaticRead) add(ReleaseItem("read", "发送并朗读", Icons.AutoMirrored.Filled.VolumeUp, VoiceReleaseTarget.READ, read))
        add(ReleaseItem("cancel", "取消", Icons.Default.Close, VoiceReleaseTarget.CANCEL, cancel))
        add(ReleaseItem("continuous", "持续识别", Icons.Default.Hearing, VoiceReleaseTarget.CONTINUOUS, continuous))
        extensions.take(5).forEach { registered -> add(ReleaseItem(registered.plugin.descriptor.id + "/" + registered.action.id,
            registered.action.label, registered.action.icon.vector(), null, { extension(registered) }, registered.action.buttonStyle == UiButtonStyle.ICON)) }
    }
    val interactions = items.map { item -> key(item.id) { remember { MutableInteractionSource() } } }
    val progress = items.mapIndexed { index, item -> key(item.id) {
        val pressed by interactions[index].collectIsPressedAsState()
        animateFloatAsState(if (pressed || selection == (item.target?.name ?: item.id)) 1f else 0f,
            spring(dampingRatio = 1f, stiffness = 1800f), label = "voice-arc-deformation").value.coerceIn(0f, 1f)
    } }
    val baseWeights = items.map { if (it.iconOnly) .55f else 1f }
    val weights = baseWeights.mapIndexed { index, base -> base * (1f + .12f * progress[index]) }
    BoxWithConstraints((if (landscape) Modifier.width(272.dp).fillMaxHeight() else Modifier.fillMaxWidth().height(272.dp))
        .testTag("voice-input-release-zones")) {
        val length = if (landscape) maxHeight.value else maxWidth.value
        val poses = voiceArcPoses(length, weights)
        val hitPoses = voiceArcPoses(length, baseWeights)
        fun bounds(pose: VoiceArcPose) = if (landscape) Rect(pose.bounds.top, length - pose.bounds.right,
            pose.bounds.bottom, length - pose.bounds.left) else pose.bounds
        items.forEachIndexed { index, item -> key(item.id) {
            val pose = poses[index]
            val rect = bounds(pose)
            val hitRect = bounds(hitPoses[index])
            val active = selection == (item.target?.name ?: item.id)
            val roundness = 1f + (minOf(12f / (pose.thickness / 2f), 1f) - 1f) * progress[index]
            val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .98f)
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .86f), MaterialTheme.motionScheme.fastEffectsSpec(), label = "voice-zone-color")
            DisposableEffect(item.id, gesture) { onDispose {
                item.target?.let(gesture::removeArea) ?: gesture.removePluginArea(item.id)
            } }
            // Hover targets stay still while their visible shapes expand and change corner radius.
            Box(Modifier.offset(hitRect.left.dp, hitRect.top.dp).size(hitRect.width.dp, hitRect.height.dp)
                .onGloballyPositioned { coordinates -> item.target?.let { gesture.area(it, coordinates.boundsInRoot()) }
                    ?: gesture.pluginArea(item.id, coordinates.boundsInRoot()) })
            Box(Modifier.offset(rect.left.dp, rect.top.dp).size(rect.width.dp, rect.height.dp)
                .clip(VoiceArcSegment(pose, landscape, roundness)).background(color)
                .clickable(interactionSource = interactions[index], indication = ripple(), onClick = feedbackClick(item.action))
                .semantics { contentDescription = item.label; selected = active }
                .testTag("voice-input-" + item.id), contentAlignment = Alignment.Center) {
                val showLabel = !item.iconOnly && (if (landscape) rect.height else rect.width) >= 82f
                VoiceArcLabel(item.label, item.icon, showLabel, pose, landscape)
            }
        } }
        val highlight = gesture.selected == VoiceReleaseTarget.SEND
        val color by animateColorAsState(if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .94f)
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .8f), MaterialTheme.motionScheme.fastEffectsSpec(), label = "voice-send-color")
        DisposableEffect(gesture) { onDispose { gesture.removeArea(VoiceReleaseTarget.SEND) } }
        Box((if (landscape) Modifier.align(Alignment.CenterEnd).width(140.dp).fillMaxHeight()
            else Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp))
            .onGloballyPositioned { gesture.area(VoiceReleaseTarget.SEND, it.boundsInRoot()) }
            .clip(VoiceSendArc(landscape)).background(color).clickable(onClick = feedbackClick(send)).testTag("voice-input-send"),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if (gesture.armed) "松开 发送" else "发送", color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
