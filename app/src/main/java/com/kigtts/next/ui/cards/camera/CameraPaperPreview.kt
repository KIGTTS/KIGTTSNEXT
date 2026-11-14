package com.kigtts.next.ui.cards.camera

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
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
import com.kigtts.next.ui.components.LocalCornerRadius
import com.kigtts.next.ui.navigation.cameraPullViewfinder
import com.kigtts.plugin.api.PolaroidFormat

/** The capture UI shows only the photograph; paper/frame decoration belongs to the finished card. */
@Composable
internal fun CameraPaperPreview(view: PreviewView, format: PolaroidFormat, filter: PhotoFilter,
    session: PolaroidCameraSession, sizeChanged: (IntSize) -> Unit, modifier: Modifier = Modifier,
    displayReady: Boolean = session.ready, morphChanged: (Boolean) -> Unit = {},
    options: CameraOptions? = null, rotation: Int = Surface.ROTATION_0, active: Boolean = true, showGuides: Boolean = true,
    filterStrength: Float = 1f, lut: com.kigtts.next.media.color.CubeLut? = null,
    verticalChrome: Boolean = false, headerOverlay: Boolean = false,
    chrome: @Composable BoxScope.(CameraChromeLayout) -> Unit = {},
    streamTransition: CameraStreamTransition? = null,
    aspectRatio: Float = format.geometry.photoAspectRatio.toFloat(),
    fullFrame: Boolean = false, photoSideInset: Float = 0f, professional: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {}) {
    val feedback = rememberUpdatedState(LocalHapticFeedback.current)
    val enabled = rememberUpdatedState(displayReady && session.ready && !session.busy && !session.countingDown)
    val motion = rememberCameraPreviewMorph(view, format, filter, displayReady, morphChanged, filterStrength, aspectRatio, fullFrame)
    val colors = rememberCameraPreviewFilter(filter, filterStrength)
    val resolvedLut = remember(filter, lut) { PhotoFilters.lut(filter, lut) }
    val effects = options?.effects ?: com.kigtts.next.media.color.PhotoEffects.NONE
    LaunchedEffect(session, colors, resolvedLut, filterStrength, effects) {
        snapshotFlow { colors.values() }.collect { session.setPreviewFilter(it, resolvedLut, filterStrength, effects) }
    }
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
        val baseline = LocalCameraCaptureBaseline.current
        val headerClearance = if (headerOverlay) minOf(CAMERA_HEADER_CLEARANCE, maxHeight.value * .5f) else 0f
        val fullFrameProgress = motion.fullFrameProgress.value
        val headerInset = headerClearance * (1f - fullFrameProgress)
        val targetHeaderInset = if (fullFrame) 0f else headerClearance
        val photoWidth = maxWidth.value - minOf(2 * photoSideInset, maxWidth.value * .5f)
        val fitted = cameraPhotoWindow(photoWidth, maxHeight.value - headerInset, motion.ratio.value)
        val axisEnd = if (verticalChrome) maxWidth.value else maxHeight.value
        val proFit by animateFloatAsState(if (professional) 1f else 0f,
            CameraFramingMotion, label = "camera-pro-preview-clearance")
        val proPreviewLimit = baseline?.let {
            val inset = (axisEnd - it.proPreviewLimit).coerceAtLeast(0f)
            (axisEnd - inset * proFit * (1f - fullFrameProgress)).takeIf { proFit > 0f && fullFrameProgress < 1f }
        }
        val layout = if (baseline != null) cameraBaselineChrome(maxWidth.value, maxHeight.value,
            cameraCenteredCaptureWindow(maxWidth.value, maxHeight.value, motion.ratio.value, verticalChrome,
                fullFrameProgress, photoSideInset, proPreviewLimit,
                portraitShift = if (verticalChrome) 0f else cameraLongScreenPreviewShift(maxWidth.value, maxHeight.value, baseline.dockStart)),
            baseline, professional)
        else rememberCameraChromeLayout(maxWidth.value, maxHeight.value, fitted,
            cameraPhotoWindow(photoWidth, maxHeight.value - targetHeaderInset, aspectRatio), verticalChrome,
            headerInset = headerInset, portraitAnchor = LocalCameraPortraitAnchor.current,
            shutterInset = LocalCameraShutterInset.current.value, photoWidthLimit = photoWidth,
            fullFrameProgress = fullFrameProgress, targetHeaderInset = targetHeaderInset, fullFrame = fullFrame)
        Box(Modifier.align(Alignment.TopStart).cameraChromeBounds(layout.photo).clip(RoundedCornerShape(LocalCornerRadius.current)).background(Color.Black)
            .testTag("camera-photo-window")) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().onSizeChanged(sizeChanged).cameraPullViewfinder())
            motion.snapshot?.let { snapshot ->
                BlurredBackdrop(snapshot, Modifier.matchParentSize().graphicsLayer { alpha = motion.alpha.value }.testTag("camera-ratio-cover"))
            }
            streamTransition?.snapshot?.let { snapshot ->
                BlurredBackdrop(snapshot, Modifier.matchParentSize().graphicsLayer { alpha = streamTransition.alpha.value }
                    .testTag("camera-stream-cover"))
            }
            CameraViewfinderPlaceholder(!displayReady && motion.snapshot == null && streamTransition?.snapshot == null)
            options?.takeIf { showGuides }?.let {
                CameraCompositionGuides(it.grid, it.level, rotation, active && displayReady, Modifier.matchParentSize())
            }
            CameraFocusOverlay(session.focusTarget, session.exposure.focusOffset, if (session.manual.automaticExposure) session.exposure.focusRange else 0..0,
                enabled.value && displayReady, session::setExposure)
            CameraCountdownOverlay(session.countdownSeconds)
            overlay()
        }
        // The same native preview remains in its photo box; only chrome is positioned in the surrounding space.
        chrome(layout)
    }
}
