@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

@Composable
internal fun CameraSettingsButton(open: () -> Unit, modifier: Modifier, shapes: IconButtonShapes,
    colors: IconButtonColors, enabled: Boolean) {
    FilledTonalIconButton(onClick = feedbackClick(open), modifier = modifier.size(44.dp),
        shapes = shapes, colors = colors, enabled = enabled) {
        Icon(Icons.Default.Tune, "相机设置", Modifier.size(22.dp))
    }
}
