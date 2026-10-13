package com.kigtts.next.ui.cards.camera

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kigtts.next.ui.media.BlurredBackdrop
import com.kigtts.plugin.api.PolaroidFormat

/** The capture UI shows only the photograph; paper/frame decoration belongs to the finished card. */
@Composable
internal fun CameraPaperPreview(view: PreviewView, format: PolaroidFormat, filter: PhotoFilter,
    session: PolaroidCameraSession, sizeChanged: (IntSize) -> Unit, modifier: Modifier = Modifier,
    displayReady: Boolean = session.ready, morphChanged: (Boolean) -> Unit = {}) {
    val feedback = rememberUpdatedState(LocalHapticFeedback.current)
    val enabled = rememberUpdatedState(displayReady && session.ready && !session.busy)
    val motion = rememberCameraPreviewMorph(view, format, filter, displayReady, morphChanged)
    DisposableEffect(view, session) {
        var pinched = false
        val scale = ScaleGestureDetector(view.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (!enabled.value) return false
                pinched = true; session.clearFocus()
                feedback.value.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate); return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean { session.zoomBy(detector.scaleFactor); return true }
            override fun onScaleEnd(detector: ScaleGestureDetector) { feedback.value.performHapticFeedback(HapticFeedbackType.GestureEnd) }
        })
        val tap = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true
            override fun onSingleTapUp(event: MotionEvent): Boolean {
                if (!enabled.value || pinched) return false
                feedback.value.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                session.focus(view, event.x, event.y); view.performClick(); return true
            }
        })
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) pinched = false
            if (event.pointerCount > 1) pinched = true
            scale.onTouchEvent(event)
            if (!pinched) tap.onTouchEvent(event)
            true
        }
        onDispose { view.setOnTouchListener(null) }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val fitted = cameraPhotoWindow(maxWidth.value, maxHeight.value, motion.ratio.value)
        Box(Modifier.size(fitted.width.dp, fitted.height.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)
            .testTag("camera-photo-window")) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().onSizeChanged(sizeChanged), update = { preview ->
                preview.setLayerType(if (filter == PhotoFilter.ORIGINAL) View.LAYER_TYPE_NONE else View.LAYER_TYPE_HARDWARE,
                    if (filter == PhotoFilter.ORIGINAL) null else Paint().apply { colorFilter = ColorMatrixColorFilter(PhotoFilters.matrix(filter)) })
            })
            motion.snapshot?.let { snapshot ->
                BlurredBackdrop(snapshot, Modifier.matchParentSize().graphicsLayer { alpha = motion.alpha.value }.testTag("camera-ratio-cover"))
            }
            CameraFocusOverlay(session.focusTarget, session.exposureIndex, session.exposureRange,
                enabled.value && displayReady, session::setExposure)
        }
    }
}
