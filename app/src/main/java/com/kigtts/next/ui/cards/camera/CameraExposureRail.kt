@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
    zoomStops: Int, visible: Boolean, enabled: Boolean, toggle: () -> Unit) {
    val copy = rememberCameraCopy()
    CameraRailAccessory(bounds, vertical, zoomStops, visible && state.range.first < state.range.last, enabled,
        false, copy.text("camera.status_ev", "value" to cameraEvLabel(state.baseline, state.step)), Icons.Default.Exposure,
        "camera-global-exposure", state.baseline != 0, toggle)
}

@Composable
internal fun BoxScope.CameraRailAccessory(bounds: Rect, vertical: Boolean, zoomStops: Int, visible: Boolean,
    enabled: Boolean, opposite: Boolean, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String, highlighted: Boolean, click: () -> Unit) {
    val padding = 48.dp * zoomStops / 2 + 28.dp
    val sign = if (opposite) -1 else 1
    Box(Modifier.align(Alignment.TopStart).cameraChromeBounds(bounds), contentAlignment = Alignment.Center) {
        val placement = if (vertical) Modifier.offset(y = padding.coerceAtMost((bounds.height / 2 - 22f).coerceAtLeast(0f).dp) * sign)
            else Modifier.offset(x = -padding.coerceAtMost((bounds.width / 2 - 22f).coerceAtLeast(0f).dp) * sign)
        AnimatedVisibility(visible, placement, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
            ToolButton(description, icon, click, Modifier.testTag(tag).excludeCameraPull(), size = 44.dp,
                shapes = IconButtonDefaults.shapes(CircleShape, MaterialTheme.shapes.small),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f),
                    contentColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    .keepColorsWhenLocked(), enabled = enabled && visible)
        }
    }
}

@Composable
internal fun CameraExposureRuler(exposure: CameraExposureCompensation, enabled: Boolean,
    change: (Int) -> Unit, close: () -> Unit, modifier: Modifier = Modifier, description: String? = null,
    outerPadding: Boolean = true, background: Boolean = true) {
    val copy = rememberCameraCopy()
    val range = exposure.range
    if (range.first >= range.last) return
    val colors = MaterialTheme.colorScheme
    Surface(modifier.then(if (outerPadding) Modifier.padding(horizontal = 4.dp) else Modifier)
        .fillMaxWidth().height(48.dp).excludeCameraPull().testTag("camera-exposure-ruler"),
        shape = CircleShape, color = if (background) colors.surfaceContainer.copy(alpha = .94f) else androidx.compose.ui.graphics.Color.Transparent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CameraRulerIcon(copy.text("camera.reset_ev"), Icons.Default.RestartAlt, "camera-global-exposure-reset",
                enabled && exposure.baseline != 0) { change(0) }
            CameraTickRuler(range, exposure.baseline, { cameraEvLabel(it, exposure.step) }, description ?: copy.text("camera.global_ev"),
                "camera-global-exposure-slider", enabled, change, Modifier.weight(1f), emphasis = 0, snapToEmphasis = true)
            ToolButton(copy.text("camera.close_ev"), Icons.Default.Close, close, Modifier.testTag("camera-global-exposure-close"),
                size = 48.dp, enabled = enabled, shapes = IconButtonDefaults.shapes(CircleShape, MaterialTheme.shapes.small))
        }
    }
}
