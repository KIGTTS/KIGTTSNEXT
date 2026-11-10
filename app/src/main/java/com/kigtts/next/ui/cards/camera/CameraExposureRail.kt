@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.keepColorsWhenLocked
import com.kigtts.next.ui.navigation.excludeCameraPull
import java.util.Locale
import kotlin.math.roundToInt

internal fun cameraEvLabel(index: Int, step: Float): String =
    if (index == 0) "0" else String.format(Locale.ROOT, "%+.1f", index * step)

@Composable
internal fun BoxScope.CameraExposureRail(state: CameraExposureCompensation, bounds: Rect, vertical: Boolean,
    zoomStops: Int, expanded: Boolean, enabled: Boolean, toggle: () -> Unit, change: (Int) -> Unit) {
    if (state.range.first >= state.range.last) return
    val padding = 48.dp * zoomStops / 2 + 28.dp
    Crossfade(expanded, Modifier.align(Alignment.TopStart).cameraChromeBounds(bounds),
        animationSpec = tween(160), label = "camera-exposure-rail") { open ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (open) {
                val ruler = if (vertical) Modifier.verticalModeLabel() else Modifier.widthIn(max = 400.dp)
                CameraExposureRuler(state, enabled && expanded, change, toggle, ruler)
            } else {
                val placement = if (vertical) Modifier.offset(y = padding.coerceAtMost((bounds.height / 2 - 22f).coerceAtLeast(0f).dp))
                    else Modifier.offset(x = -padding.coerceAtMost((bounds.width / 2 - 22f).coerceAtLeast(0f).dp))
                ToolButton("曝光补偿 " + cameraEvLabel(state.baseline, state.step), Icons.Default.Exposure, toggle,
                    placement.testTag("camera-global-exposure").excludeCameraPull(), size = 44.dp,
                    shapes = IconButtonDefaults.shapes(CircleShape, MaterialTheme.shapes.small),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f),
                        contentColor = if (state.baseline != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        .keepColorsWhenLocked(), enabled = enabled && !expanded)
            }
        }
    }
}

@Composable
private fun CameraExposureRuler(exposure: CameraExposureCompensation, enabled: Boolean,
    change: (Int) -> Unit, close: () -> Unit, modifier: Modifier = Modifier) {
    val range = exposure.range
    if (range.first >= range.last) return
    val colors = MaterialTheme.colorScheme
    Surface(modifier.padding(horizontal = 4.dp).fillMaxWidth().height(48.dp).excludeCameraPull().testTag("camera-exposure-ruler"),
        shape = CircleShape, color = colors.surfaceContainer.copy(alpha = .94f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolButton("重置曝光补偿", Icons.Default.RestartAlt, { change(0) },
                Modifier.testTag("camera-global-exposure-reset"), size = 48.dp, enabled = enabled && exposure.baseline != 0,
                shapes = IconButtonDefaults.shapes(CircleShape, MaterialTheme.shapes.small))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cameraEvLabel(range.first, exposure.step), style = MaterialTheme.typography.labelSmall)
                    Text(cameraEvLabel(exposure.baseline, exposure.step), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                    Text(cameraEvLabel(range.last, exposure.step), style = MaterialTheme.typography.labelSmall)
                }
                val haptic = LocalHapticFeedback.current
                val slider = key(range) { rememberSliderState(exposure.baseline.toFloat(),
                    steps = (range.last - range.first - 1).coerceAtLeast(0), valueRange = range.first.toFloat()..range.last.toFloat()) }
                slider.value = exposure.baseline.toFloat()
                slider.onValueChange = {
                    val next = it.roundToInt()
                    if (next != exposure.baseline) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        change(next)
                    }
                }
                Slider(slider, Modifier.fillMaxWidth().height(27.dp).cameraSliderPressFeedback()
                    .testTag("camera-global-exposure-slider").semantics { contentDescription = "全局曝光补偿" },
                    enabled = enabled, thumb = {
                        Canvas(Modifier.size(3.dp, 21.dp)) {
                            drawLine(colors.primary, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), size.width)
                        }
                    }, track = {
                        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                            val count = range.last - range.first
                            val stride = (count / 32 + 1).coerceAtLeast(1)
                            (range step stride).forEach { tick ->
                                val x = size.width * (tick - range.first) / count
                                val strong = tick == 0 || tick == range.first || tick == range.last
                                val radius = if (strong) .42f else .23f
                                drawLine(colors.onSurface.copy(alpha = if (strong) .7f else .38f),
                                    Offset(x, size.height * (.5f - radius)), Offset(x, size.height * (.5f + radius)), 1.dp.toPx())
                            }
                        }
                    })
            }
            ToolButton("收起曝光补偿", Icons.Default.Close, close, Modifier.testTag("camera-global-exposure-close"),
                size = 48.dp, enabled = enabled, shapes = IconButtonDefaults.shapes(CircleShape, MaterialTheme.shapes.small))
        }
    }
}
