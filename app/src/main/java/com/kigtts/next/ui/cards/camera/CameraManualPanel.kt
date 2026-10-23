package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.settings.*

/** A collapsible settings destination holds manual controls; camera HUD keeps the active mode visible. */
@Composable
internal fun CameraManualPanel(controller: CameraManualController, enabled: Boolean) {
    val capability = controller.capabilities
    SettingsGroup("手动调整") {
        SettingItem("对焦", capability.reason ?: if (!capability.focusAvailable) "当前镜头不支持手动对焦" else "",
            Icons.Default.CenterFocusStrong, SettingPosition.FIRST) {
            Column {
                ChoiceButtonGroup("对焦", listOf(false, true), controller.manualFocus,
                    { if (it) "手动" else "自动" }, controller::focusMode, enabled = enabled && capability.focusAvailable)
                AnimatedVisibility(controller.manualFocus) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("远"); Text("近") }
                        Slider(controller.focusDiopters, controller::focusAt, Modifier.testTag("camera-manual-focus"),
                            enabled = enabled, valueRange = 0f..capability.maximumFocus.coerceAtLeast(.001f))
                    }
                }
            }
        }
        SettingItem("快门", capability.reason ?: if (!capability.shutterAvailable) "当前镜头不支持手动快门" else "",
            Icons.Default.ShutterSpeed, SettingPosition.LAST) {
            Column {
                ChoiceButtonGroup("快门", listOf(false, true), !controller.automaticExposure,
                    { if (it) "手动" else "自动" }, controller::setManualShutter, enabled = enabled && capability.shutterAvailable)
                val range = capability.exposure
                AnimatedVisibility(!controller.automaticExposure && range != null) {
                    if (range != null) Column {
                        Text(shutterLabel(controller.shutterNanos ?: range.first) + " · ISO " + controller.sensorIso,
                            Modifier.testTag("camera-manual-shutter-value"), style = MaterialTheme.typography.labelLarge)
                        Slider(shutterFraction(controller.shutterNanos ?: range.first, range),
                            { controller.exposeFor(shutterAt(it, range)) }, Modifier.testTag("camera-manual-shutter"), enabled = enabled)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            shutterStops(range).forEach { nanos -> MotionTextButton(feedbackClick { controller.exposeFor(nanos) },
                                enabled = enabled) { Text(shutterLabel(nanos)) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CameraManualBadge(controller: CameraManualController, modifier: Modifier = Modifier) {
    val labels = buildList {
        if (controller.manualFocus) add("MF")
        controller.shutterNanos?.let { add(shutterLabel(it)) }
    }
    AnimatedVisibility(labels.isNotEmpty(), modifier) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f)) {
            Text(labels.joinToString(" · "), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}
