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
import androidx.compose.ui.unit.em
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
    val typography = MaterialTheme.typography
    val valueStyle = if (vertical) typography.labelLarge.copy(lineHeight = 1.25.em) else typography.labelLarge
    val captionStyle = if (vertical) typography.labelSmall.copy(lineHeight = 1.25.em) else typography.labelSmall
    val valueAutoSize = remember(vertical, valueStyle.fontSize) {
        if (vertical) TextAutoSize.StepBased(8.sp, valueStyle.fontSize, .5.sp) else null
    }
    val captionAutoSize = remember(vertical, captionStyle.fontSize) {
        if (vertical) TextAutoSize.StepBased(8.sp, captionStyle.fontSize, .5.sp) else null
    }
    Surface(modifier.excludeCameraPull().testTag("camera-pro-controls"),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .90f), shape = MaterialTheme.shapes.large) {
        val reset: @Composable () -> Unit = {
            CameraRulerIcon(copy.text("camera.pro_reset"), Icons.Default.RestartAlt, "camera-pro-reset", enabled) {
                select(null); controller.reset(); changeEv(0)
            }
        }
        val parameter: @Composable (ProControl, Modifier) -> Unit = { key, slot ->
            val value = when (key) {
                ProControl.EV -> cameraEvLabel(exposure.baseline, exposure.step)
                ProControl.SHUTTER -> (controller.shutterNanos ?: measured?.exposure)?.takeIf { it > 0 }?.let {
                    shutterDisplayValue(it)
                } ?: copy.text("option.auto")
                ProControl.ISO -> controller.sensorIso.takeIf { it > 0 }?.toString() ?: measured?.iso?.toString() ?: copy.text("option.auto")
                ProControl.WB -> controller.whiteBalanceKelvin?.let(::cameraKelvinLabel) ?: copy.label(controller.whiteBalance)
                ProControl.FOCUS -> if (controller.manualFocus) "MF" else "AF"
            }
            val isSelected = activeControl == key
            TextButton(feedbackClick { select(if (isSelected) null else key) },
                slot.testTag("camera-pro/${key.name}").semantics { this.selected = isSelected },
                enabled = enabled && proControlAvailable(key, controller, exposure), contentPadding = PaddingValues(horizontal = 2.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        style = valueStyle, autoSize = valueAutoSize)
                    Text(key.short, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        style = captionStyle, autoSize = captionAutoSize)
                }
            }
        }
        if (vertical) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                ProControl.entries.asReversed().forEach { parameter(it, Modifier.weight(1f).fillMaxWidth()) }
                reset()
            }
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                reset()
                ProControl.entries.forEach { parameter(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

internal fun proControlAvailable(key: ProControl, controller: CameraManualController, exposure: CameraExposureCompensation): Boolean = when (key) {
    ProControl.EV -> controller.automaticExposure && exposure.step > 0f && exposure.range.first < exposure.range.last
    ProControl.SHUTTER, ProControl.ISO -> controller.capabilities.shutterAvailable
    ProControl.WB -> controller.temperatureRange() != null || controller.capabilities.whiteBalance.any { it != CameraWhiteBalance.AUTO }
    ProControl.FOCUS -> controller.capabilities.focusAvailable
}
