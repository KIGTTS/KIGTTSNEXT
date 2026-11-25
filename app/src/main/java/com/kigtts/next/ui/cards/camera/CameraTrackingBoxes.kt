package com.kigtts.next.ui.cards.camera

import android.graphics.RectF
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** The focus boxes belong only to camera chrome, independent of the recorded stickers. */
@Composable internal fun CameraTrackingBoxes(view: PreviewView, session: PolaroidCameraSession, modifier: Modifier) {
    Canvas(modifier) {
        val frame = session.trackedFaces?.takeIf { it.isCurrent() } ?: return@Canvas
        val target = view.outputTransform ?: return@Canvas
        if (view.width <= 0 || view.height <= 0) return@Canvas
        val transform = frame.toView(target)
        val sx = size.width / view.width
        val sy = size.height / view.height
        for (face in frame.faces) {
            val rect = RectF(face.bounds)
            transform.mapRect(rect)
            if (rect.width() <= 0 || rect.height() <= 0) continue
            val alpha = .84f * face.opacity
            drawRoundRect(Color.Black.copy(alpha = alpha * .55f), Offset(rect.left * sx, rect.top * sy),
                Size(rect.width() * sx, rect.height() * sy), CornerRadius(5.dp.toPx()), style = Stroke(2.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = alpha), Offset(rect.left * sx, rect.top * sy),
                Size(rect.width() * sx, rect.height() * sy), CornerRadius(5.dp.toPx()), style = Stroke(1.dp.toPx()))
        }
    }
}
