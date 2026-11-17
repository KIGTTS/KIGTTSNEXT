@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.keepColorsWhenLocked
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.excludeCameraPull

@Composable
internal fun CameraMotionButton(requested: Boolean, available: Boolean, ready: Boolean, preparing: Boolean,
    enabled: Boolean, change: (Boolean) -> Unit) {
    val copy = rememberCameraCopy()
    val status = when { !requested -> copy.text("action.close"); preparing -> copy.text("camera.preparing"); ready -> copy.text("camera.enabled"); !available -> copy.text("camera.lens_unavailable"); else -> copy.text("camera.preparing") }
    Box(Modifier.size(44.dp).excludeCameraPull()) {
        ToolButton(copy.text("camera.status_motion", "value" to status), if (requested) Icons.Default.MotionPhotosOn else Icons.Default.MotionPhotosOff,
            { change(!requested) }, Modifier.size(44.dp).testTag("camera-motion").semantics { selected = requested },
            shapes = IconButtonDefaults.shapes(LocalCameraControlShape.current ?: CircleShape, RoundedCornerShape(10.dp)),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (requested) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f),
                contentColor = if (requested) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                .let { if (!LocalPageActive.current) it.keepColorsWhenLocked() else it },
            size = 44.dp, enabled = enabled)
        if (requested && preparing) CircularProgressIndicator(Modifier.size(40.dp).align(Alignment.Center), strokeWidth = 2.dp)
    }
}
