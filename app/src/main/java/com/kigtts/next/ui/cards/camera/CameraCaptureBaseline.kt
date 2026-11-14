package com.kigtts.next.ui.cards.camera

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect

private const val STAGE_VERTICAL_PADDING = 4f
internal const val CAMERA_PRO_BAND = 54f
internal const val CAMERA_RAIL_GAP = 8f
private const val CAMERA_BASELINE_GAP = 4f
private const val CAMERA_ZOOM_BAND = 48f
internal const val CAMERA_LEFT_CLEARANCE = 60f
private const val REFERENCE_DEVICE_RATIO = 21f / 9f
private const val REFERENCE_PORTRAIT_DOCK = 140f
private const val MIN_DOCK_TRAILING_GAP = 12f
private const val MAX_DOCK_TRAILING_GAP = 164f
private const val MIN_DOCK_START = 72f
private const val CAMERA_DOCK_RAIL_SPACE = CAMERA_PRO_BAND + CAMERA_BASELINE_GAP + CAMERA_PREVIEW_SIDE_INSET

/** Geometry in dp, relative to the padded camera workspace, before any UI rotation. */
internal data class CameraCaptureBaseline(val frame: Rect, val dockStart: Float, val vertical: Boolean) {
    private val railEnd get() = (dockStart - CAMERA_BASELINE_GAP).coerceAtLeast(0f)
    fun pro(): Rect = band(railEnd, CAMERA_PRO_BAND)
    fun zoom(pro: Boolean): Rect = if (pro) {
        band((pro().let { depthEnd(it.left, it.top) } - CAMERA_RAIL_GAP).coerceAtLeast(0f), CAMERA_ZOOM_BAND)
    } else {
        band((railEnd - (CAMERA_PRO_BAND - CAMERA_ZOOM_BAND) / 2).coerceAtLeast(0f), CAMERA_ZOOM_BAND)
    }
    // Leave the non-full preview a gap before the professional strip; never move the dock.
    val proPreviewLimit get() = (railEnd - CAMERA_PRO_BAND - CAMERA_PREVIEW_SIDE_INSET).coerceAtLeast(0f)
    private fun depthEnd(x: Float, y: Float) = if (vertical) x else y
    private fun band(end: Float, depth: Float): Rect = if (vertical) {
        Rect((end - depth).coerceAtLeast(0f), frame.top, end, frame.bottom)
    } else Rect(frame.left, (end - depth).coerceAtLeast(0f), frame.right, end)
}

internal val LocalCameraCaptureBaseline = compositionLocalOf<CameraCaptureBaseline?> { null }

internal fun cameraCaptureBaseline(width: Float, stageHeight: Float, vertical: Boolean, dockExtent: Float): CameraCaptureBaseline {
    val height = (stageHeight - 2 * STAGE_VERTICAL_PADDING).coerceAtLeast(0f)
    val photo = cameraCenteredCaptureWindow(width, height, if (vertical) CameraAspect.STANDARD.ratio(4, 3) else CameraAspect.STANDARD.ratio(3, 4), vertical, 0f)
    val long = if (vertical) width else stageHeight
    val short = if (vertical) stageHeight else width
    val available = if (vertical) width else height
    val lastDockStart = (available - dockExtent).coerceAtLeast(0f)
    val trailing = cameraDockTrailingGap(long, short)
    // The action bar is 92dp deep in either orientation, but the mode strip is wider in
    // landscape. Reserve its measured extent without changing the shutter's trailing gap.
    val dockStart = (lastDockStart - trailing + if (vertical) 0f else STAGE_VERTICAL_PADDING)
        .coerceIn(minOf(MIN_DOCK_START, lastDockStart), lastDockStart)
    return CameraCaptureBaseline(photo, dockStart, vertical)
}

/** Preserve the familiar portrait thumb reach at 21:9 and use it as the rotated reference. */
internal fun cameraDockTrailingGap(longSide: Float, shortSide: Float): Float {
    val referenceLength = shortSide * REFERENCE_DEVICE_RATIO
    val referenceFrameEnd = CAMERA_HEADER_CLEARANCE + CAMERA_PREVIEW_SIDE_INSET +
        (shortSide - 2 * CAMERA_PREVIEW_SIDE_INSET).coerceAtLeast(0f) / CameraAspect.STANDARD.ratio(3, 4)
    val referenceGap = (referenceLength - STAGE_VERTICAL_PADDING - referenceFrameEnd -
        CAMERA_DOCK_RAIL_SPACE - REFERENCE_PORTRAIT_DOCK).coerceIn(MIN_DOCK_TRAILING_GAP, MAX_DOCK_TRAILING_GAP)
    // Squatter displays need more room for the viewfinder. Longer phones keep the 21:9 reach.
    return (referenceGap - (referenceLength - longSide).coerceAtLeast(0f))
        .coerceAtLeast(MIN_DOCK_TRAILING_GAP)
}

internal fun CameraCaptureBaseline.dockPositionInStage(): Float = dockStart + if (vertical) 0f else STAGE_VERTICAL_PADDING

internal fun cameraCenteredCaptureWindow(width: Float, height: Float, ratio: Float, vertical: Boolean,
    fullscreenProgress: Float, sideInset: Float = CAMERA_PREVIEW_SIDE_INSET, endLimit: Float? = null): Rect {
    val header = minOf(if (vertical) CAMERA_LEFT_CLEARANCE else CAMERA_HEADER_CLEARANCE,
        (if (vertical) width else height) / 2) * (1f - fullscreenProgress)
    val side = minOf(sideInset, minOf(width, height) * .25f)
    val mainGap = side * (1f - fullscreenProgress)
    // When chrome rotates, its clearance rotates too. The edges keep the same breathing room.
    val left = if (vertical) header + side else side
    val top = if (vertical) mainGap else header + mainGap
    val rightEdge = width - side
    val bottomEdge = height - mainGap
    // Very small windows can overlap the rails instead of losing the camera preview entirely.
    val right = endLimit?.takeIf { vertical && it - left >= 48f }?.coerceAtMost(rightEdge) ?: rightEdge
    val bottom = endLimit?.takeIf { !vertical && it - top >= 48f }?.coerceAtMost(bottomEdge) ?: bottomEdge
    val size = cameraPhotoWindow(right - left, bottom - top, ratio)
    // Portrait starts below its controls; the rotated non-full layout starts next to its
    // controls too. Don't add half the landscape letterbox to that left clearance.
    val centerX = left + (right - left - size.width).coerceAtLeast(0f) * .5f
    val x = if (vertical) left + (centerX - left) * fullscreenProgress else centerX
    val y = if (vertical) top + (bottom - top - size.height).coerceAtLeast(0f) * .5f else top
    return Rect(x, y, x + size.width, y + size.height)
}
