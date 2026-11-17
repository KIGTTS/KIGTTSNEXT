package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The native preview stays measured underneath; no camera opens while the page is being pulled in. */
@Composable
internal fun BoxScope.CameraViewfinderPlaceholder(visible: Boolean) {
    val copy = rememberCameraCopy()
    AnimatedVisibility(visible, Modifier.matchParentSize(), enter = fadeIn(tween(120)), exit = fadeOut(tween(180))) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag("camera-viewfinder-placeholder"), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PhotoCamera, copy.text("camera.viewfinder"), Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f))
        }
    }
}
