@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HdrAuto
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.next.ui.navigation.excludeCameraPull
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun CameraProAdjustment(key: ProControl, controller: CameraManualController, sample: CameraMeteringSample?,
    enabled: Boolean, modifier: Modifier, background: Boolean = true) {
    val copy = rememberCameraCopy()
    val capability = controller.capabilities
    val auto = when (key) {
        ProControl.FOCUS -> !controller.manualFocus
        ProControl.WB -> controller.automaticWhiteBalance
        else -> controller.automaticExposure
    }
    Surface(modifier.fillMaxWidth().height(48.dp).excludeCameraPull(),
        shape = CircleShape, color = if (background) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .94f) else Color.Transparent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = MaterialSymbols.vector("hdr_auto") ?: Icons.Default.HdrAuto
            CameraRulerIcon(copy.text("camera.status_reset_auto", "value" to copy.label(key)), icon, "camera-pro-auto", enabled && !auto) {
                when (key) {
                    ProControl.FOCUS -> controller.focusMode(false)
                    ProControl.WB -> controller.selectWhiteBalance(CameraWhiteBalance.AUTO)
                    else -> controller.setManualShutter(false)
                }
            }

            val ruler = Modifier.weight(1f).padding(end = 12.dp)
            when (key) {
                ProControl.SHUTTER -> capability.exposure?.let { range ->
                    val current = (controller.shutterNanos ?: sample?.exposure ?: 16_666_667L).coerceIn(range)
                    CameraContinuousRuler(0f..1f, shutterFraction(current, range), { shutterDisplayValue(shutterAt(it, range)) },
                        copy.label(key), "camera-manual-shutter", enabled, {
                            val requested = shutterAt(it, range)
                            if (controller.automaticExposure) controller.setManualShutter(true, time = requested)
                            else controller.exposeFor(requested)
                        }, ruler, if (auto) copy.text("option.auto") else shutterDisplayValue(current))
                }
                ProControl.ISO -> capability.sensitivity?.let { range ->
                    val longRange = range.first.toLong()..range.last.toLong()
                    val current = (controller.sensorIso.takeIf { it > 0 } ?: sample?.iso ?: range.first).coerceIn(range)
                    CameraContinuousRuler(0f..1f, shutterFraction(current.toLong(), longRange), { shutterAt(it, longRange).toString() },
                        copy.label(key), "camera-manual-iso", enabled, {
                            val requested = shutterAt(it, longRange).toInt()
                            if (controller.automaticExposure) controller.setManualShutter(true, sensitivity = requested)
                            else controller.sensitivityAt(requested)
                        }, ruler, if (auto) copy.text("option.auto") else current.toString())
                }
                ProControl.FOCUS -> {
                    val max = capability.maximumFocus
                    val distance = (if (auto) sample?.focus?.takeIf(Float::isFinite) ?: 0f else controller.focusDiopters).coerceIn(0f, max)
                    val label: (Float) -> String = { if (it == 0f) "∞" else String.format(Locale.ROOT, "%.2f D", it) }
                    CameraContinuousRuler(0f..max, distance, label, copy.label(key), "camera-manual-focus", enabled, {
                        if (!controller.manualFocus) controller.focusMode(true, it) else controller.focusAt(it)
                    }, ruler, if (auto) copy.text("option.auto") else label(distance), emphasis = 0f, snapToEmphasis = true)
                }
                ProControl.WB -> {
                    val range = controller.temperatureRange(sample)
                    if (range != null) {
                        val current = (controller.whiteBalanceKelvin ?: sample?.temperature ?: CAMERA_WB_DAYLIGHT_KELVIN).coerceIn(range)
                        CameraContinuousRuler(range.first.toFloat()..range.last.toFloat(), current.toFloat(),
                            { cameraKelvinLabel(it.roundToInt()) }, copy.label(key), "camera-manual-white-balance", enabled,
                            { controller.temperatureAt(it.roundToInt()) }, ruler,
                            if (auto) copy.text("option.auto") else cameraKelvinLabel(current))
                    } else {
                        val presets = remember(capability.whiteBalance) {
                            CameraWhiteBalance.entries.filter { it == CameraWhiteBalance.AUTO || it in capability.whiteBalance }
                        }
                        if (presets.size > 1) {
                            CameraTickRuler(presets.indices, presets.indexOf(controller.whiteBalance).coerceAtLeast(0),
                                { copy.label(presets[it]) }, copy.label(key), "camera-manual-white-balance", enabled,
                                { controller.selectWhiteBalance(presets[it]) }, ruler, emphasis = 0)
                        }
                    }
                }
                ProControl.EV -> Unit
            }
        }
    }
}
