@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.keepColorsWhenLocked
import com.kigtts.next.ui.navigation.excludeCameraPull

/** Floating safe-area actions only: this layer neither paints an app bar nor takes viewport height. */
@Composable
internal fun CameraCornerToolbar(active: Boolean, busy: Boolean, configurationEnabled: Boolean, loading: Boolean,
    back: () -> Unit, settings: () -> Unit, applyInsets: Boolean = true,
    vertical: Boolean = false, showSettings: Boolean = true, extraAction: @Composable () -> Unit = {}) {
    val shape = IconButtonDefaults.shapes(RoundedCornerShape(22.dp), RoundedCornerShape(10.dp))
    val colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f),
        contentColor = MaterialTheme.colorScheme.onSurface).let { if (!active) it.keepColorsWhenLocked() else it }
    Box(Modifier.fillMaxSize().then(if (applyInsets) Modifier.safeDrawingPadding() else Modifier).padding(horizontal = 8.dp, vertical = 8.dp)
        .testTag("camera-corner-toolbar")) {
        Box(Modifier.align(if (vertical) Alignment.TopEnd else Alignment.TopStart).size(44.dp).excludeCameraPull()) {
            ToolButton("返回", Icons.Default.ArrowBack, back, Modifier.fillMaxSize().testTag("camera-cancel"),
                size = 44.dp, shapes = shape, colors = colors, enabled = active && !busy)
            AnimatedVisibility(loading, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
                CircularProgressIndicator(Modifier.size(40.dp).testTag("camera-header-loading"), strokeWidth = 2.dp)
            }
        }
        Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            extraAction()
            if (showSettings) ToolButton("相机设置", Icons.Default.Tune, settings,
                Modifier.testTag("camera-settings").excludeCameraPull(),
                size = 44.dp, shapes = shape, colors = colors, enabled = active && configurationEnabled)
        }
    }
}
