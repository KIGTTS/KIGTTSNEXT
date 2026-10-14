package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

internal data class CameraChromeSpace(val controlsOutside: Boolean, val zoomOutside: Boolean)
internal data class CameraChromeLayout(val photo: Rect, val controls: Rect, val zoom: Rect,
    val space: CameraChromeSpace)

private const val CAMERA_CHROME_GAP = 8f
private const val CAMERA_CHROME_EDGE = 4f
private const val CAMERA_CONTROLS_SIZE = 56f
private const val CAMERA_ZOOM_HEIGHT = 48f

/** All dimensions are in dp. Only real unused space may host an external touch target. */
internal fun cameraChromeSpace(width: Float, height: Float, photo: Size, vertical: Boolean,
    headerInset: Float = 0f): CameraChromeSpace {
    val margin = if (vertical) (width - photo.width) / 2 else (height - photo.height) / 2
    return CameraChromeSpace(margin >= CAMERA_CONTROLS_SIZE + CAMERA_CHROME_GAP + CAMERA_CHROME_EDGE + (if (vertical) 0f else headerInset),
        margin >= (if (vertical) CAMERA_CONTROLS_SIZE else CAMERA_ZOOM_HEIGHT) + CAMERA_CHROME_GAP + CAMERA_CHROME_EDGE)
}

/** Interpolate placements without reserving new layout space or changing the photograph's viewport. */
internal fun cameraChromeLayout(width: Float, height: Float, photo: Size, vertical: Boolean,
    controlsProgress: Float, zoomProgress: Float, space: CameraChromeSpace, headerInset: Float = 0f): CameraChromeLayout {
    val left = ((width - photo.width) / 2).coerceAtLeast(0f)
    val top = ((height - photo.height) / 2).coerceAtLeast(0f)
    val window = Rect(left, top, left + photo.width, top + photo.height)
    fun move(from: Float, to: Float, progress: Float) = from + (to - from) * progress.coerceIn(0f, 1f)
    val controls: Rect
    val zoom: Rect
    if (vertical) {
        val size = minOf(CAMERA_CONTROLS_SIZE, width)
        val outsideLeft = (left - CAMERA_CHROME_GAP - size).coerceAtLeast(CAMERA_CHROME_EDGE).coerceAtMost((width - size).coerceAtLeast(0f))
        val controlsLeft = move(left, outsideLeft, controlsProgress)
        // Begin making room before the strip reaches the floating Back target, rather than jump at its edge.
        val cornerReserve = (headerInset * 2f - controlsLeft).coerceIn(0f, headerInset)
        val controlsTop = maxOf(top, cornerReserve).coerceAtMost(window.bottom)
        controls = Rect(controlsLeft, controlsTop, controlsLeft + size, window.bottom)
        val insideRight = (window.right - size).coerceAtLeast(0f)
        val outsideRight = (window.right + CAMERA_CHROME_GAP).coerceAtMost((width - size - CAMERA_CHROME_EDGE).coerceAtLeast(0f))
        val zoomLeft = move(insideRight, outsideRight, zoomProgress)
        val zoomTop = maxOf(top + photo.height * .05f, headerInset + CAMERA_CHROME_GAP)
        val zoomBottom = maxOf(zoomTop, window.bottom - photo.height * .05f)
        // A compact rail sits close to the shutter. The two camera corner actions retain their own strip.
        val railHeight = minOf(240f, zoomBottom - zoomTop)
        val railTop = zoomTop + (zoomBottom - zoomTop - railHeight) / 2f
        zoom = Rect(zoomLeft, railTop, zoomLeft + size, railTop + railHeight)
    } else {
        val controlsHeight = minOf(CAMERA_CONTROLS_SIZE, height)
        val outsideTop = (top - CAMERA_CHROME_GAP - controlsHeight).coerceAtLeast(CAMERA_CHROME_EDGE + headerInset)
            .coerceAtMost((height - controlsHeight).coerceAtLeast(0f))
        val controlsTop = move(maxOf(top, headerInset).coerceAtMost((height - controlsHeight).coerceAtLeast(0f)), outsideTop, controlsProgress)
        controls = Rect(left, controlsTop, window.right, controlsTop + controlsHeight)
        val zoomHeight = minOf(CAMERA_ZOOM_HEIGHT, height)
        val insideBottom = (window.bottom - zoomHeight).coerceAtLeast(0f)
        val outsideBottom = (window.bottom + CAMERA_CHROME_GAP).coerceAtMost((height - zoomHeight - CAMERA_CHROME_EDGE).coerceAtLeast(0f))
        val zoomTop = move(insideBottom, outsideBottom, zoomProgress)
        zoom = Rect(left, zoomTop, window.right, zoomTop + zoomHeight)
    }
    return CameraChromeLayout(window, controls, zoom, space)
}

@Composable
internal fun rememberCameraChromeLayout(width: Float, height: Float, photo: Size, targetPhoto: Size,
    vertical: Boolean, headerInset: Float = 0f): CameraChromeLayout {
    // The decision uses the final ratio once; intermediate animation sizes cannot toggle the placement back and forth.
    val space = cameraChromeSpace(width, height, targetPhoto, vertical, headerInset)
    val controls by animateFloatAsState(if (space.controlsOutside) 1f else 0f,
        tween(280, easing = FastOutSlowInEasing), label = "camera-controls-placement")
    val zoom by animateFloatAsState(if (space.zoomOutside) 1f else 0f,
        tween(280, easing = FastOutSlowInEasing), label = "camera-zoom-placement")
    return cameraChromeLayout(width, height, photo, vertical, controls, zoom, space, headerInset)
}

internal fun Modifier.cameraChromeBounds(rect: Rect): Modifier = offset(rect.left.dp, rect.top.dp)
    .size(rect.width.coerceAtLeast(0f).dp, rect.height.coerceAtLeast(0f).dp)
