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
import com.kigtts.next.ui.navigation.excludeCameraPull

@Composable
internal fun CameraMotionButton(requested: Boolean, available: Boolean, ready: Boolean, preparing: Boolean,
    enabled: Boolean, change: (Boolean) -> Unit) {
    val status = when { !requested -> "关闭"; preparing -> "准备中"; ready -> "已开启"; !available -> "此镜头不可用"; else -> "准备中" }
    Box(Modifier.size(44.dp).excludeCameraPull()) {
        ToolButton("实况照片：$status", if (requested) Icons.Default.MotionPhotosOn else Icons.Default.MotionPhotosOff,
            { change(!requested) }, Modifier.size(44.dp).testTag("camera-motion").semantics { selected = requested },
            shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(10.dp)),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (requested) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f),
                contentColor = if (requested) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface),
            size = 44.dp, enabled = enabled)
        if (requested && preparing) CircularProgressIndicator(Modifier.size(40.dp).align(Alignment.Center), strokeWidth = 2.dp)
    }
}
