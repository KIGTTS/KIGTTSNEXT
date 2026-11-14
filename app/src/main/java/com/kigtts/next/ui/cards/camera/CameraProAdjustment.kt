package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.navigation.excludeCameraPull
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
internal fun CameraProAdjustment(key: ProControl, controller: CameraManualController, sample: CameraMeteringSample?,
    enabled: Boolean, modifier: Modifier) {
    val capability = controller.capabilities
    val auto = when (key) {
        ProControl.FOCUS -> !controller.manualFocus
        ProControl.WB -> controller.whiteBalance == CameraWhiteBalance.AUTO
        else -> controller.automaticExposure
    }
    Surface(modifier.fillMaxWidth().height(48.dp).excludeCameraPull(),
        shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .94f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(feedbackClick {
                when (key) {
                    ProControl.FOCUS -> controller.focusMode(false)
                    ProControl.WB -> controller.selectWhiteBalance(CameraWhiteBalance.AUTO)
                    else -> controller.setManualShutter(false)
                }
            }, Modifier.width(48.dp).testTag("camera-pro-auto"), shape = CircleShape,
                enabled = enabled && !auto, contentPadding = PaddingValues(0.dp)) { Text("自动") }

            val ruler = Modifier.weight(1f).padding(end = 12.dp)
            when (key) {
                ProControl.SHUTTER -> capability.exposure?.let { range ->
                    val stops = remember(range) { cameraProLogStops(range) }
                    val current = controller.shutterNanos ?: sample?.exposure ?: 16_666_667L
                    CameraTickRuler(stops.indices, stops.closestStop(current), { shutterLabel(stops[it]).removeSuffix(" 秒") },
                        key.label, "camera-manual-shutter", enabled, {
                            if (controller.automaticExposure) controller.setManualShutter(true, time = stops[it])
                            else controller.exposeFor(stops[it])
                        }, ruler, if (auto) "自动" else shutterLabel(current).removeSuffix(" 秒"))
                }
                ProControl.ISO -> capability.sensitivity?.let { range ->
                    val stops = remember(range) { cameraProLogStops(range.first.toLong()..range.last.toLong()) }
                    val current = controller.sensorIso.takeIf { it > 0 } ?: sample?.iso ?: range.first
                    CameraTickRuler(stops.indices, stops.closestStop(current.toLong()), { stops[it].toString() },
                        key.label, "camera-manual-iso", enabled, {
                            if (controller.automaticExposure) controller.setManualShutter(true, sensitivity = stops[it].toInt())
                            else controller.sensitivityAt(stops[it].toInt())
                        }, ruler, if (auto) "自动" else current.toString())
                }
                ProControl.FOCUS -> {
                    val max = capability.maximumFocus
                    val distance = if (auto) sample?.focus?.takeIf(Float::isFinite) ?: 0f else controller.focusDiopters
                    val ticks = 30
                    CameraTickRuler(0..ticks, (distance.coerceIn(0f, max) / max * ticks).roundToInt(),
                        { if (it == 0) "∞" else String.format(Locale.ROOT, "%.1f D", it * max / ticks) },
                        key.label, "camera-manual-focus", enabled, {
                            val requested = it * max / ticks
                            if (!controller.manualFocus) controller.focusMode(true, requested)
                            else controller.focusAt(requested)
                        }, ruler, if (auto) "自动" else if (distance == 0f) "∞" else String.format(Locale.ROOT, "%.1f D", distance))
                }
                ProControl.WB -> {
                    val presets = remember(capability.whiteBalance) {
                        CameraWhiteBalance.entries.filter { it == CameraWhiteBalance.AUTO || it in capability.whiteBalance }
                    }
                    if (presets.size > 1) {
                        CameraTickRuler(presets.indices, presets.indexOf(controller.whiteBalance).coerceAtLeast(0),
                            { presets[it].label }, key.label, "camera-manual-white-balance", enabled,
                            { controller.selectWhiteBalance(presets[it]) }, ruler, emphasis = 0)
                    }
                }
                ProControl.EV -> Unit
            }
        }
    }
}

internal fun cameraProLogStops(range: LongRange): List<Long> =
    (listOf(range.first) + (1 until 30).map { shutterAt(it / 30f, range) } + range.last).distinct()

private fun List<Long>.closestStop(value: Long): Int {
    val target = ln(value.coerceAtLeast(1).toDouble())
    return indices.minBy { abs(ln(this[it].toDouble()) - target) }
}
