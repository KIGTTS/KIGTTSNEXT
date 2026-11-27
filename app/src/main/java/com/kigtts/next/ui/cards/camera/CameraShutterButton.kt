@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

@Composable
internal fun cameraShutterAccent(): Color =
    if (LocalCameraShutterOverlay.current) Color.White else MaterialTheme.colorScheme.primary

@Composable
internal fun cameraRecordAccent(): Color {
    val colors = MaterialTheme.colorScheme
    // The recording mark sits on a dark backing when the controls cover the viewfinder.
    return if (LocalCameraShutterOverlay.current && colors.error.luminance() < colors.errorContainer.luminance())
        colors.errorContainer else colors.error
}

/** The pointer owner stays mounted and enabled through a long press and its final release. */
@Composable
internal fun CameraShutterButton(enabled: Boolean, busy: Boolean, countingDown: Boolean,
    capture: () -> Unit, modifier: Modifier = Modifier, burstStart: (() -> Unit)? = null,
    burstEnd: () -> Unit = {}, burstRunning: Boolean = false, burstHeld: Boolean = false, count: Int = 0,
    temporary: Boolean = false) {
    val copy = rememberCameraCopy()
    var pressed by remember { mutableStateOf(false) }
    val click = rememberUpdatedState(feedbackClick(capture))
    val start = rememberUpdatedState(burstStart); val end = rememberUpdatedState(burstEnd)
    val running = rememberUpdatedState(burstRunning)
    val feedback = LocalHapticFeedback.current
    val corner by animateDpAsState(if (pressed || burstHeld) 20.dp else 38.dp, tween(130), label = "shutter-shape")
    val colors = MaterialTheme.colorScheme
    val overlay = LocalCameraShutterOverlay.current
    val fill = cameraShutterAccent()
    val label = when {
        burstHeld -> copy.text("camera.burst_release")
        burstRunning -> copy.text("camera.preparing_photos")
        countingDown -> copy.text("camera.cancel_timer")
        temporary -> copy.text("camera.temporary_shutter")
        else -> copy.text("camera.take_photo")
    }
    fun longPress() { start.value?.let { feedback.performHapticFeedback(HapticFeedbackType.LongPress); it() } }
    Surface(modifier.size(76.dp).semantics {
        role = Role.Button; contentDescription = label
        if (!enabled) disabled()
        if (burstRunning) stateDescription = copy.text("camera.burst_count", "count" to count.toString())
        onClick { if (enabled) { if (running.value) end.value() else click.value() }; enabled }
        if (burstStart != null) onLongClick(copy.text("camera.start_burst")) { if (enabled) longPress(); enabled }
    }.pointerInput(enabled, burstStart != null) {
        if (enabled) detectTapGestures(
            onTap = { if (running.value) end.value() else click.value() },
            onLongPress = if (burstStart != null) ({ longPress() }) else null,
            onPress = {
                pressed = true
                try { tryAwaitRelease() } finally { pressed = false; end.value() }
            })
    }, shape = RoundedCornerShape(corner),
        border = BorderStroke(3.dp, fill.copy(alpha = if (enabled || busy) 1f else .35f)),
        color = if (enabled || busy) fill else fill.copy(alpha = .35f),
        contentColor = if (overlay) Color.Black else colors.onPrimary) {
        androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            if (burstRunning) Text("$count", style = MaterialTheme.typography.headlineMedium)
            else if (countingDown || busy) Icon(if (countingDown) Icons.Default.TimerOff else Icons.Default.HourglassEmpty, null, Modifier.size(30.dp))
            else Icon(Icons.Default.PhotoCamera, null, Modifier.size(if (temporary) 26.dp else 32.dp))
        }
    }
}

@Composable
internal fun CameraBurstStatus(burst: CameraBurstCapture, modifier: Modifier = Modifier) {
    val copy = rememberCameraCopy()
    androidx.compose.animation.AnimatedVisibility(burst.running && !burst.held, modifier,
        enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                Text(if (burst.count == 0) copy.text("camera.receive_photos") else copy.text("camera.burst_processing", "done" to burst.processed.toString(), "total" to burst.count.toString()))
            }
        }
    }
}
