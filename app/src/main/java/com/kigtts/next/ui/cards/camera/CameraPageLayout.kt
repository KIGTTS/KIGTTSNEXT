package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal val LocalCameraShutterOverlay = staticCompositionLocalOf { false }
internal val LocalCameraShutterInset = compositionLocalOf { 0.dp }
internal val LocalCameraPortraitAnchor = compositionLocalOf { 0f }
internal val LocalCameraStageSettled = compositionLocalOf { true }

/** Stable sibling slots: aspect changes only reposition, never reparent the live preview. */
@Composable
internal fun CameraPageLayout(preview: @Composable (Modifier, Boolean) -> Unit,
    controls: @Composable (Boolean) -> Unit, error: @Composable () -> Unit = {},
    header: @Composable (Boolean) -> Unit = {}, photoAspect: Float? = null, zoomDialVisible: Boolean = false,
    fullFrame: Boolean = false, captureBaseline: Boolean = false, professional: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().testTag("polaroid-camera")) {
        val vertical = cameraSideControls(maxWidth.value, maxHeight.value)
        var controlsSize by remember { mutableStateOf(IntSize.Zero) }
        val inset = with(LocalDensity.current) { (if (vertical) controlsSize.width else controlsSize.height).toDp() }
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val safeDrawing = WindowInsets.safeDrawing
        val trailingInset = if (!captureBaseline) 0
            else if (vertical) safeDrawing.getRight(density, direction) else safeDrawing.getBottom(density)
        val baseline = if (captureBaseline) cameraCaptureBaseline(maxWidth.value, maxHeight.value, vertical, inset.value) else null
        val overlay = if (baseline != null && photoAspect != null) {
            val stageHeight = (maxHeight.value - 8f).coerceAtLeast(0f)
            val photo = baseline.preview(maxWidth.value, stageHeight, photoAspect, if (fullFrame) 1f else 0f,
                professionalProgress = if (professional) 1f else 0f)
            (if (vertical) photo.right else photo.bottom) > baseline.dockStart + .5f
        } else fullFrame || cameraOverlaysShutter(maxWidth.value, maxHeight.value, photoAspect)
        val targetOverlap = if (overlay) 1f else 0f
        val overlap by animateFloatAsState(targetOverlap, CameraFramingMotion, label = "camera-dock-overlap")
        val dockSpace = if (!captureBaseline && !vertical && photoAspect != null) (maxHeight * .075f - 12.dp).coerceIn(0.dp, 52.dp) else 0.dp
        val blend by animateFloatAsState(if (zoomDialVisible) 1f else 0f,
            tween(if (zoomDialVisible) 140 else 180), label = "camera-dial-dock")
        Layout(content = {
            Box(Modifier.testTag("camera-stage")) {
                CompositionLocalProvider(
                    LocalCameraShutterInset provides (inset * overlap),
                    LocalCameraPortraitAnchor provides (if (!vertical && photoAspect != null) 1f - overlap else 0f),
                    LocalCameraStageSettled provides (overlap == targetOverlap),
                    LocalCameraCaptureBaseline provides baseline,
                ) {
                    preview(Modifier.fillMaxSize().padding(vertical = 4.dp), vertical)
                }
                header(vertical)
                Box(Modifier.align(if (overlay) Alignment.TopCenter else Alignment.BottomCenter)
                    .fillMaxWidth().then(if (overlay) Modifier.padding(top = 54.dp) else Modifier)) { error() }
            }
            CameraDockBackdrop(vertical, overlap, blend, inset)
            Box(Modifier.onSizeChanged { controlsSize = it }) {
                Column {
                    CompositionLocalProvider(LocalCameraShutterOverlay provides overlay) { controls(vertical) }
                    if (dockSpace > 0.dp) Spacer(Modifier.height(dockSpace))
                }
            }
        }, modifier = Modifier.fillMaxSize()) { children, constraints ->
            val width = constraints.maxWidth; val height = constraints.maxHeight
            val dock = children[2].measure(Constraints(0, width, 0, height))
            // Keep the dock fixed. Only its reserved space changes as the preview grows underneath it.
            val reserved = if (captureBaseline) 0f else 1f - overlap
            val stage = children[0].measure(Constraints.fixed(
                if (vertical) (width - (dock.width * reserved).roundToInt()).coerceAtLeast(0) else width,
                if (vertical) height else (height - (dock.height * reserved).roundToInt()).coerceAtLeast(0)))
            val start = if (captureBaseline) cameraCaptureBaseline(width.toDp().value, height.toDp().value, vertical,
                (if (vertical) dock.width else dock.height).toDp().value).dockPositionInStage().dp.roundToPx()
            else if (vertical) width - dock.width else height - dock.height
            val backdropDepth = if (captureBaseline)
                ((if (vertical) width else height) - start + trailingInset).coerceAtLeast(0)
                else if (vertical) dock.width else dock.height
            val backdrop = children[1].measure(Constraints.fixed(
                if (vertical) backdropDepth else dock.width, if (vertical) dock.height else backdropDepth))
            layout(width, height) {
                stage.place(0, 0)
                backdrop.place(if (vertical) start else 0, if (vertical) 0 else start)
                dock.place(if (vertical) start else 0, if (vertical) 0 else start)
            }
        }
    }
}


@Composable
private fun CameraDockBackdrop(vertical: Boolean, coverage: Float, dial: Float, dockDepth: Dp) {
    Box(Modifier.testTag("camera-dock-backdrop").drawBehind {
        if (coverage <= 0f) return@drawBehind
        val depth = (if (vertical) size.width else size.height).coerceAtLeast(1f)
        val stop = (dockDepth.toPx() / depth).coerceIn(.01f, 1f)
        val ink = Color.Black.copy(alpha = .88f)
        val stops = arrayOf(0f to Color.Transparent, stop * .5f to Color.Black.copy(alpha = .68f), stop to ink, 1f to ink)
        val scrim = if (vertical) Brush.horizontalGradient(*stops) else Brush.verticalGradient(*stops)
        drawRect(scrim, alpha = coverage * (1f - dial))
        drawRect(CameraZoomDialBackdrop, alpha = coverage * dial)
    })
}
