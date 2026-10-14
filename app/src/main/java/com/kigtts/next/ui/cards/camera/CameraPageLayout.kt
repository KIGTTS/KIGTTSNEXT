package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun CameraPageLayout(preview: @Composable (Modifier, Boolean) -> Unit,
    controls: @Composable (Boolean) -> Unit, error: @Composable () -> Unit = {},
    header: @Composable () -> Unit = {}, photoAspect: Float? = null) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp).testTag("polaroid-camera")) {
        if (cameraSideControls(maxWidth.value, maxHeight.value)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    // Keep the camera actions near its image on wide windows, away from the shutter rail.
                    val stageWidth = photoAspect?.let { minOf(maxWidth, maxHeight * it + 128.dp) } ?: maxWidth
                    Box(Modifier.width(stageWidth).fillMaxHeight().testTag("camera-stage")) {
                        preview(Modifier.fillMaxSize().padding(vertical = 4.dp), true)
                        header()
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { error() }
                    }
                }
                controls(true)
            }
        } else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                val stageHeight = photoAspect?.let { minOf(maxHeight, maxWidth / it + 168.dp) } ?: maxHeight
                Box(Modifier.fillMaxWidth().height(stageHeight).testTag("camera-stage")) {
                    preview(Modifier.fillMaxSize().padding(vertical = 4.dp), false)
                    header()
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { error() }
                }
            }
            controls(false)
        }
    }
}
