@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.navigation.excludeCameraPull
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class ProControl(val label: String, val short: String) {
    EV("曝光补偿", "EV"), SHUTTER("快门", "S"), ISO("感光度", "ISO"), WB("白平衡", "WB"), FOCUS("对焦", "F")
}

/** Manual parameters live beside the mode selector instead of behind the settings sheet. */
@Composable
internal fun CameraProControls(controller: CameraManualController, exposure: CameraExposureCompensation,
    enabled: Boolean, vertical: Boolean, evExpanded: Boolean, toggleEv: () -> Unit, resetEv: () -> Unit) {
    val capability = controller.capabilities
    var selected by remember { mutableStateOf<ProControl?>(null) }
    var measured by remember(controller) { mutableStateOf<CameraMeteringSample?>(null) }
    LaunchedEffect(enabled, controller) {
        if (enabled) while (true) {
            measured = controller.meteringSample()
            delay(300)
        } else measured = null
    }
    fun available(key: ProControl): Boolean = when (key) {
        ProControl.EV -> controller.automaticExposure && exposure.step > 0f
        ProControl.SHUTTER, ProControl.ISO -> capability.shutterAvailable
        ProControl.WB -> capability.whiteBalance.any { it != CameraWhiteBalance.AUTO }
        ProControl.FOCUS -> capability.focusAvailable
    }
    val activeControl = selected?.takeIf { available(it) && !evExpanded }
    val options: @Composable () -> Unit = {
        AnimatedVisibility(activeControl != null) {
            val orientation = if (vertical) Modifier.verticalModeLabel() else Modifier
            // Keep the previous contents during the exit animation.
            var last by remember { mutableStateOf(activeControl) }
            SideEffect { if (activeControl != null) last = activeControl }
            (activeControl ?: last)?.let { CameraProAdjustment(it, controller, enabled && activeControl != null, orientation) }
        }
    }
    val parameters: @Composable () -> Unit = {
        val orientation = if (vertical) Modifier.verticalModeLabel() else Modifier
        Surface(orientation.widthIn(max = 480.dp).fillMaxWidth().height(54.dp).excludeCameraPull().testTag("camera-pro-controls"),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .90f), shape = MaterialTheme.shapes.large) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolButton("恢复自动", Icons.Default.RestartAlt, {
                    selected = null; controller.reset(); resetEv()
                }, Modifier.testTag("camera-pro-reset"), size = 44.dp, enabled = enabled)
                ProControl.entries.forEach { key ->
                    val value = when (key) {
                        ProControl.EV -> cameraEvLabel(exposure.baseline, exposure.step)
                        ProControl.SHUTTER -> (controller.shutterNanos ?: measured?.exposure)?.takeIf { it > 0 }?.let {
                            shutterLabel(it).removeSuffix(" 秒")
                        } ?: "自动"
                        ProControl.ISO -> controller.sensorIso.takeIf { it > 0 }?.toString() ?: measured?.iso?.toString() ?: "自动"
                        ProControl.WB -> controller.whiteBalance.label
                        ProControl.FOCUS -> if (controller.manualFocus) "MF" else "AF"
                    }
                    val isSelected = if (key == ProControl.EV) evExpanded else activeControl == key
                    TextButton(feedbackClick {
                        if (key == ProControl.EV) { selected = null; toggleEv() }
                        else { if (evExpanded) toggleEv(); selected = if (selected == key) null else key }
                    }, Modifier.weight(1f).fillMaxHeight().testTag("camera-pro/${key.name}").semantics { this.selected = isSelected },
                        enabled = enabled && available(key), contentPadding = PaddingValues(horizontal = 2.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            Text(key.short, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    if (vertical) Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) { options(); parameters() }
    else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { options(); parameters() }
}

@Composable
private fun CameraProAdjustment(key: ProControl, controller: CameraManualController, enabled: Boolean, modifier: Modifier) {
    val capability = controller.capabilities
    Surface(modifier.widthIn(max = 480.dp).fillMaxWidth().height(54.dp).excludeCameraPull(),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .94f)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (key == ProControl.WB) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf(CameraWhiteBalance.AUTO) + capability.whiteBalance).distinct().forEach { mode ->
                        FilterChip(controller.whiteBalance == mode, feedbackClick { controller.selectWhiteBalance(mode) },
                            label = { Text(mode.label) }, enabled = enabled)
                    }
                }
                return@Row
            }
            val auto = if (key == ProControl.FOCUS) !controller.manualFocus else controller.automaticExposure
            TextButton(feedbackClick {
                if (key == ProControl.FOCUS) controller.focusMode(auto) else controller.setManualShutter(auto)
            }, Modifier.testTag("camera-pro-auto"), enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (auto) "自动" else "手动")
            }
            when (key) {
                ProControl.SHUTTER -> capability.exposure?.let { range ->
                    Slider(shutterFraction(controller.shutterNanos ?: range.first, range), { controller.exposeFor(shutterAt(it, range)) },
                        Modifier.weight(1f).testTag("camera-manual-shutter").cameraSliderPressFeedback(), enabled = enabled && !auto)
                }
                ProControl.ISO -> capability.sensitivity?.let { range ->
                    val long = range.first.toLong()..range.last.toLong()
                    Slider(shutterFraction(controller.sensorIso.toLong(), long), { controller.sensitivityAt(shutterAt(it, long).toInt()) },
                        Modifier.weight(1f).testTag("camera-manual-iso").cameraSliderPressFeedback(), enabled = enabled && !auto)
                }
                ProControl.FOCUS -> Slider(controller.focusDiopters, controller::focusAt,
                    Modifier.weight(1f).testTag("camera-manual-focus").cameraSliderPressFeedback(),
                    enabled = enabled && !auto, valueRange = 0f..capability.maximumFocus.coerceAtLeast(.001f))
                else -> Unit
            }
        }
    }
}
