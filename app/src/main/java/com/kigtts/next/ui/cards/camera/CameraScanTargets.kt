package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.navigation.excludeCameraPull

@Composable
internal fun CameraScanTargets(frame: CameraCodeFrame, dismiss: () -> Unit, choose: (String) -> Unit) {
    val copy = rememberCameraCopy()
    val image = remember(frame.bitmap) { frame.bitmap.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).excludeCameraPull()
        .pointerInput(Unit) { awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
        } }.testTag("camera-scan-frozen")) {
        val scale = minOf(maxWidth.value / image.width, maxHeight.value / image.height)
        val width = (image.width * scale).dp; val height = (image.height * scale).dp
        Box(Modifier.align(Alignment.Center).size(width, height)) {
            Image(image, null, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .50f)))
        }
        frame.codes.forEachIndexed { index, code -> code.center?.let { point ->
            val x = ((maxWidth - width) / 2 + width * point.x - 22.dp).coerceIn(0.dp, (maxWidth - 44.dp).coerceAtLeast(0.dp))
            val y = ((maxHeight - height) / 2 + height * point.y - 22.dp).coerceIn(0.dp, (maxHeight - 44.dp).coerceAtLeast(0.dp))
            FilledIconButton(feedbackClick { choose(code.text) }, Modifier.offset(x, y).size(44.dp)
                .testTag("camera-scan-target/$index"), shape = CircleShape) { Icon(Icons.AutoMirrored.Filled.ArrowForward, copy.text("camera.status_qr", "number" to (index + 1).toString())) }
        } }
        FilledTonalIconButton(feedbackClick(dismiss), Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("camera-scan-resume")) {
            Icon(Icons.Default.Close, copy.text("camera.scan_again"))
        }
    }
}
