@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

internal enum class ProControl(val label: String, val short: String) {
    EV("曝光补偿", "EV"), SHUTTER("快门", "S"), ISO("感光度", "ISO"), WB("白平衡", "WB"), FOCUS("对焦", "F")
}
/** Stable parameter band; the ruler opens toward the shutter without resizing the preview. */
@Composable
internal fun CameraProControls(controller: CameraManualController, exposure: CameraExposureCompensation,
    enabled: Boolean, vertical: Boolean, changeEv: (Int) -> Unit, modifier: Modifier = Modifier,
    selected: ProControl? = null, select: (ProControl?) -> Unit = {}) {
    val capability = controller.capabilities
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
    val activeControl = selected?.takeIf { available(it) }
    val options: @Composable () -> Unit = {
        AnimatedVisibility(activeControl != null) {
            val orientation = if (vertical) Modifier.verticalModeLabel() else Modifier
            // Keep the previous contents during the exit animation.
            var last by remember { mutableStateOf(activeControl) }
            SideEffect { if (activeControl != null) last = activeControl }
            (activeControl ?: last)?.let {
                val open = enabled && activeControl != null
                if (it == ProControl.EV) CameraExposureRuler(exposure, open, changeEv, { select(null) }, orientation,
                    "专业曝光补偿", outerPadding = false)
                else key(it) { CameraProAdjustment(it, controller, measured, open, orientation) }
            }
        }
    }
    val parameters: @Composable () -> Unit = {
        val orientation = if (vertical) Modifier.verticalModeLabel() else Modifier
        Surface(orientation.fillMaxWidth().height(54.dp).excludeCameraPull().testTag("camera-pro-controls"),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .90f), shape = MaterialTheme.shapes.large) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolButton("恢复自动", Icons.Default.RestartAlt, {
                    select(null); controller.reset(); changeEv(0)
                }, Modifier.testTag("camera-pro-reset"), size = 48.dp, enabled = enabled,
                    shapes = IconButtonDefaults.shapes(CircleShape, CircleShape))
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
                    val isSelected = activeControl == key
                    TextButton(feedbackClick { select(if (isSelected) null else key) }, Modifier.weight(1f).fillMaxHeight().testTag("camera-pro/${key.name}").semantics { this.selected = isSelected },
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
    if (vertical) Row(modifier, verticalAlignment = Alignment.CenterVertically) { parameters(); options() }
    else Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { parameters(); options() }
}
