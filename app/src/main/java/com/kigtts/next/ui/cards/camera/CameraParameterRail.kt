@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton

/** A single centered capsule for zoom and the currently edited parameter. */
@Composable
internal fun BoxScope.CameraParameterRail(session: PolaroidCameraSession, bounds: Rect, vertical: Boolean,
    zoomEnabled: Boolean, rulerEnabled: Boolean, selection: ProControl?, globalExposure: Boolean, close: () -> Unit,
    dialVisibilityChanged: (Boolean) -> Unit, exposureEnabled: Boolean, expose: (() -> Unit)? = null) {
    val copy = rememberCameraCopy()
    val active = selection?.takeIf { proControlAvailable(it, session.manual, session.exposure) }
    var measured by remember { mutableStateOf<CameraMeteringSample?>(null) }
    LaunchedEffect(active, rulerEnabled) {
        if (active != null && rulerEnabled && active != ProControl.EV) {
            while (true) {
                measured = session.manual.meteringSample()
                kotlinx.coroutines.delay(300)
            }
        } else measured = null
    }
    CameraZoomRail(session.zoomRatio, session.minZoomRatio, session.maxZoomRatio, zoomEnabled,
        session::zoomTo, vertical, Modifier.matchParentSize(), bounds, dialVisibilityChanged, adjustment = active,
        presetAccessory = { visible, stops ->
            CameraExposureRail(session.exposure, bounds, vertical, stops, visible && expose != null, exposureEnabled) { expose?.invoke() }
        }) { key ->
        if (key == ProControl.EV) {
            CameraExposureRuler(session.exposure, rulerEnabled && key == active, session::setGlobalExposure,
                close, Modifier.fillMaxSize(), if (globalExposure) copy.text("camera.global_ev") else copy.text("camera.pro_ev"), outerPadding = false, background = false)
        } else CameraProAdjustment(key, session.manual, measured, rulerEnabled && key == active, Modifier.fillMaxSize(), background = false)
    }
}

@Composable
internal fun CameraRulerIcon(description: String, icon: ImageVector, tag: String, enabled: Boolean, click: () -> Unit) {
    ToolButton(description, icon, click, Modifier.testTag(tag), size = 48.dp, enabled = enabled,
        shapes = IconButtonDefaults.shapes(CircleShape, MaterialTheme.shapes.small))
}
