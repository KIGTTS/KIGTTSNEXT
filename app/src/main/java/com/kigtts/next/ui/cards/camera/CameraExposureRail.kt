@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.keepColorsWhenLocked
import com.kigtts.next.ui.navigation.excludeCameraPull
import java.util.Locale

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
internal fun CameraExposureRuler(exposure: CameraExposureCompensation, enabled: Boolean,
    change: (Int) -> Unit, close: () -> Unit, modifier: Modifier = Modifier, description: String = "全局曝光补偿",
    outerPadding: Boolean = true) {
    val range = exposure.range
    if (range.first >= range.last) return
    val colors = MaterialTheme.colorScheme
    Surface(modifier.then(if (outerPadding) Modifier.padding(horizontal = 4.dp) else Modifier)
        .fillMaxWidth().height(48.dp).excludeCameraPull().testTag("camera-exposure-ruler"),
        shape = CircleShape, color = colors.surfaceContainer.copy(alpha = .94f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolButton("重置曝光补偿", Icons.Default.RestartAlt, { change(0) },
                Modifier.testTag("camera-global-exposure-reset"), size = 48.dp, enabled = enabled && exposure.baseline != 0,
                shapes = IconButtonDefaults.shapes(CircleShape, MaterialTheme.shapes.small))
            CameraTickRuler(range, exposure.baseline, { cameraEvLabel(it, exposure.step) }, description,
                "camera-global-exposure-slider", enabled, change, Modifier.weight(1f), emphasis = 0)
            ToolButton("收起曝光补偿", Icons.Default.Close, close, Modifier.testTag("camera-global-exposure-close"),
                size = 48.dp, enabled = enabled, shapes = IconButtonDefaults.shapes(CircleShape, MaterialTheme.shapes.small))
        }
    }
}
