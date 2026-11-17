package com.kigtts.next.ui.cards.camera

import com.kigtts.next.i18n.uiText
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
    val copy = rememberCameraCopy()
    val manualLabel = uiText("option.manual")
    val autoLabel = uiText("option.auto")
    SettingsGroup(uiText("camera.manual_adjust")) {
        SettingItem(uiText("camera.focus"), capability.reason?.let { if (it.startsWith("camera.")) uiText(it) else it } ?: if (!capability.focusAvailable) uiText("camera.no_manual_focus") else "",
            Icons.Default.CenterFocusStrong, SettingPosition.FIRST) {
            Column {
                ChoiceButtonGroup(uiText("camera.focus"), listOf(false, true), controller.manualFocus,
                    { if (it) manualLabel else autoLabel }, { controller.focusMode(it) }, enabled = enabled && capability.focusAvailable)
                AnimatedVisibility(controller.manualFocus) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(uiText("camera.far")); Text(uiText("camera.near")) }
                        Slider(controller.focusDiopters, controller::focusAt, Modifier.testTag("camera-manual-focus"),
                            enabled = enabled, valueRange = 0f..capability.maximumFocus.coerceAtLeast(.001f))
                    }
                }
            }
        }
        SettingItem(uiText("camera.shutter"), capability.reason?.let { if (it.startsWith("camera.")) uiText(it) else it } ?: if (!capability.shutterAvailable) uiText("camera.no_manual_shutter") else "",
            Icons.Default.ShutterSpeed, SettingPosition.LAST) {
            Column {
                ChoiceButtonGroup(uiText("camera.shutter"), listOf(false, true), !controller.automaticExposure,
                    { if (it) manualLabel else autoLabel }, { controller.setManualShutter(it) }, enabled = enabled && capability.shutterAvailable)
                val range = capability.exposure
                AnimatedVisibility(!controller.automaticExposure && range != null) {
                    if (range != null) Column {
                        Text(copy.shutter(controller.shutterNanos ?: range.first) + " · ISO " + controller.sensorIso,
                            Modifier.testTag("camera-manual-shutter-value"), style = MaterialTheme.typography.labelLarge)
                        Slider(shutterFraction(controller.shutterNanos ?: range.first, range),
                            { controller.exposeFor(shutterAt(it, range)) }, Modifier.testTag("camera-manual-shutter"), enabled = enabled)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            shutterStops(range).forEach { nanos -> MotionTextButton(feedbackClick { controller.exposeFor(nanos) },
                                enabled = enabled) { Text(copy.shutter(nanos)) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CameraManualBadge(controller: CameraManualController, modifier: Modifier = Modifier) {
    val copy = rememberCameraCopy()
    val labels = buildList {
        if (controller.manualFocus) add("MF")
        controller.shutterNanos?.let { add(copy.shutter(it)) }
    }
    AnimatedVisibility(labels.isNotEmpty(), modifier) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f)) {
            Text(labels.joinToString(" · "), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}
