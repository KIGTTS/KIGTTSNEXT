@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.unit.sp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.navigation.excludeCameraPull
import kotlinx.coroutines.delay

internal enum class ProControl(val label: String, val short: String) {
    EV("曝光补偿", "EV"), SHUTTER("快门", "S"), ISO("感光度", "ISO"), WB("白平衡", "WB"), FOCUS("对焦", "F")
}

@Composable
internal fun CameraProRail(visible: Boolean, controller: CameraManualController, exposure: CameraExposureCompensation,
    enabled: Boolean, vertical: Boolean, changeEv: (Int) -> Unit, modifier: Modifier,
    selected: ProControl? = null, select: (ProControl?) -> Unit = {}) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
        CameraProControls(controller, exposure, enabled && visible, vertical, changeEv, Modifier.fillMaxSize(), selected, select)
    }
}

/** The parameter band overlays the reference frame; the zoom capsule houses the active ruler. */
@Composable
internal fun CameraProControls(controller: CameraManualController, exposure: CameraExposureCompensation,
    enabled: Boolean, vertical: Boolean, changeEv: (Int) -> Unit, modifier: Modifier = Modifier,
    selected: ProControl? = null, select: (ProControl?) -> Unit = {}) {
    val copy = rememberCameraCopy()
    var measured by remember(controller) { mutableStateOf<CameraMeteringSample?>(null) }
    LaunchedEffect(enabled, controller) {
        if (enabled) while (true) {
            measured = controller.meteringSample()
            delay(300)
        } else measured = null
    }
    val activeControl = selected?.takeIf { proControlAvailable(it, controller, exposure) }
    val valueStyle = MaterialTheme.typography.labelLarge
    val captionStyle = MaterialTheme.typography.labelSmall
    val valueAutoSize = remember(vertical, valueStyle.fontSize) {
        if (vertical) TextAutoSize.StepBased(8.sp, valueStyle.fontSize, .5.sp) else null
    }
    val captionAutoSize = remember(vertical, captionStyle.fontSize) {
        if (vertical) TextAutoSize.StepBased(8.sp, captionStyle.fontSize, .5.sp) else null
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        // Measure with the rotated viewport so each label fits its actual slot.
        val orientation = if (vertical) Modifier.fillMaxSize().verticalModeLabel() else Modifier.fillMaxSize()
        Surface(orientation.excludeCameraPull().testTag("camera-pro-controls"),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .90f), shape = MaterialTheme.shapes.large) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CameraRulerIcon(copy.text("camera.pro_reset"), Icons.Default.RestartAlt, "camera-pro-reset", enabled) {
                    select(null); controller.reset(); changeEv(0)
                }
                ProControl.entries.forEach { key ->
                    val value = when (key) {
                        ProControl.EV -> cameraEvLabel(exposure.baseline, exposure.step)
                        ProControl.SHUTTER -> (controller.shutterNanos ?: measured?.exposure)?.takeIf { it > 0 }?.let {
                            shutterDisplayValue(it)
                        } ?: copy.text("option.auto")
                        ProControl.ISO -> controller.sensorIso.takeIf { it > 0 }?.toString() ?: measured?.iso?.toString() ?: copy.text("option.auto")
                        ProControl.WB -> copy.label(controller.whiteBalance)
                        ProControl.FOCUS -> if (controller.manualFocus) "MF" else "AF"
                    }
                    val isSelected = activeControl == key
                    TextButton(feedbackClick { select(if (isSelected) null else key) }, Modifier.weight(1f).fillMaxHeight().testTag("camera-pro/${key.name}").semantics { this.selected = isSelected },
                        enabled = enabled && proControlAvailable(key, controller, exposure), contentPadding = PaddingValues(horizontal = 2.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                style = valueStyle, autoSize = valueAutoSize)
                            Text(key.short, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                style = captionStyle, autoSize = captionAutoSize)
                        }
                    }
                }
            }
        }
    }
}

internal fun proControlAvailable(key: ProControl, controller: CameraManualController, exposure: CameraExposureCompensation): Boolean = when (key) {
    ProControl.EV -> controller.automaticExposure && exposure.step > 0f && exposure.range.first < exposure.range.last
    ProControl.SHUTTER, ProControl.ISO -> controller.capabilities.shutterAvailable
    ProControl.WB -> controller.capabilities.whiteBalance.any { it != CameraWhiteBalance.AUTO }
    ProControl.FOCUS -> controller.capabilities.focusAvailable
}
