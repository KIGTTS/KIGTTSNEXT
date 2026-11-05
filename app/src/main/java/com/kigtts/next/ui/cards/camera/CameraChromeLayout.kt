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

internal data class CameraChromeLayout(val photo: Rect, val controls: Rect, val zoom: Rect)

private const val CAMERA_CHROME_GAP = 8f
private const val CAMERA_CHROME_EDGE = 4f
private const val CAMERA_CONTROLS_SIZE = 56f
private const val CAMERA_ZOOM_HEIGHT = 48f
private const val CAMERA_ZOOM_INSET = 12f

/** All dimensions are in dp. Only real unused space may host an external touch target. */
internal fun cameraControlsOutside(width: Float, height: Float, photo: Size, vertical: Boolean,
    headerInset: Float = 0f, anchorTop: Float? = null): Boolean {
    val startMargin = if (vertical) (width - photo.width) / 2 else anchorTop ?: (height - photo.height) / 2
    return startMargin >= CAMERA_CONTROLS_SIZE + CAMERA_CHROME_GAP + CAMERA_CHROME_EDGE + (if (vertical) 0f else headerInset)
}

/** Interpolate placements without reserving new layout space or changing the photograph's viewport. */
internal fun cameraChromeLayout(width: Float, height: Float, photo: Size, vertical: Boolean,
    controlsProgress: Float, headerInset: Float = 0f, anchorTop: Float? = null, shutterInset: Float = 0f): CameraChromeLayout {
    val left = ((width - photo.width) / 2).coerceAtLeast(0f)
    val top = (anchorTop ?: (height - photo.height) / 2).coerceAtLeast(0f)
    val window = Rect(left, top, left + photo.width, top + photo.height)
    fun move(from: Float, to: Float, progress: Float) = from + (to - from) * progress.coerceIn(0f, 1f)
    val controls: Rect
    if (vertical) {
        val size = minOf(CAMERA_CONTROLS_SIZE, width)
        val outsideLeft = (left - CAMERA_CHROME_GAP - size).coerceAtLeast(CAMERA_CHROME_EDGE).coerceAtMost((width - size).coerceAtLeast(0f))
        val controlsLeft = move(left, outsideLeft, controlsProgress)
        // Begin making room before the strip reaches the floating Back target, rather than jump at its edge.
        val cornerReserve = (headerInset * 2f - controlsLeft).coerceIn(0f, headerInset)
        val controlsTop = maxOf(top, cornerReserve).coerceAtMost(window.bottom)
        controls = Rect(controlsLeft, controlsTop, controlsLeft + size, window.bottom)
    } else {
        val controlsHeight = minOf(CAMERA_CONTROLS_SIZE, height)
        val outsideTop = (top - CAMERA_CHROME_GAP - controlsHeight).coerceAtLeast(CAMERA_CHROME_EDGE + headerInset)
            .coerceAtMost((height - controlsHeight).coerceAtLeast(0f))
        val controlsTop = move(maxOf(top, headerInset).coerceAtMost((height - controlsHeight).coerceAtLeast(0f)), outsideTop, controlsProgress)
        controls = Rect(left, controlsTop, window.right, controlsTop + controlsHeight)
    }
    val zoom = if (vertical) {
        val right = (width - shutterInset - CAMERA_CHROME_GAP - CAMERA_ZOOM_INSET).coerceAtLeast(0f)
        val railHeight = minOf(240f, height)
        val railTop = (height - railHeight) / 2
        Rect((right - CAMERA_CONTROLS_SIZE).coerceAtLeast(0f), railTop, right, railTop + railHeight)
    } else {
        val bottom = (height - shutterInset - CAMERA_CHROME_GAP - CAMERA_ZOOM_INSET).coerceAtLeast(0f)
        Rect(0f, (bottom - CAMERA_ZOOM_HEIGHT).coerceAtLeast(0f), width, bottom)
    }
    return CameraChromeLayout(window, controls, zoom)
}

@Composable
internal fun rememberCameraChromeLayout(width: Float, height: Float, photo: Size, targetPhoto: Size,
    vertical: Boolean, headerInset: Float = 0f, anchorPortrait: Boolean = false, shutterInset: Float = 0f): CameraChromeLayout {
    val preferredTop = if (!vertical && anchorPortrait)
        (height - cameraPhotoWindow(width, height, .75f).height) / 2 else null
    fun anchor(photoHeight: Float) = preferredTop?.coerceIn(0f, (height - photoHeight).coerceAtLeast(0f))
    // The decision uses the final ratio once; intermediate animation sizes cannot toggle the placement back and forth.
    val outside = cameraControlsOutside(width, height, targetPhoto, vertical, headerInset, anchor(targetPhoto.height))
    val controls by animateFloatAsState(if (outside) 1f else 0f,
        tween(280, easing = FastOutSlowInEasing), label = "camera-controls-placement")
    return cameraChromeLayout(width, height, photo, vertical, controls, headerInset, anchor(photo.height), shutterInset)
}

internal fun Modifier.cameraChromeBounds(rect: Rect): Modifier = offset(rect.left.dp, rect.top.dp)
    .size(rect.width.coerceAtLeast(0f).dp, rect.height.coerceAtLeast(0f).dp)
