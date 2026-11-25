package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.LocalCornerRadius

/** Filters and face stickers share the same viewfinder panel, rail clearance and motion. */
@Composable internal fun BoxScope.CameraViewfinderPicker(visible: Boolean, vertical: Boolean, layout: CameraChromeLayout?,
    content: @Composable () -> Unit) {
    Box(if (layout == null) Modifier.matchParentSize()
        else Modifier.align(Alignment.TopStart).cameraChromeBounds(layout.photo).clip(RoundedCornerShape(LocalCornerRadius.current))) {
        val clearance = if (layout == null) 60f else if (vertical)
            (layout.photo.right - layout.zoom.left + 8f).coerceAtLeast(8f)
        else (layout.photo.bottom - layout.zoom.top + 8f).coerceAtLeast(8f)
        AnimatedVisibility(visible,
            modifier = if (vertical) Modifier.align(Alignment.CenterEnd).width((144f + clearance).dp).fillMaxHeight()
                else Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = fadeIn() + if (vertical) expandHorizontally(expandFrom = Alignment.End) else expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + if (vertical) shrinkHorizontally(shrinkTowards = Alignment.End) else shrinkVertically(shrinkTowards = Alignment.Bottom)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RectangleShape) {
                Box(if (vertical) Modifier.padding(end = clearance.dp) else Modifier.padding(bottom = clearance.dp)) { content() }
            }
        }
    }
}
