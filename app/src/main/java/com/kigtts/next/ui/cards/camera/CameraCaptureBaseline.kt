package com.kigtts.next.ui.cards.camera

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect

private const val STAGE_VERTICAL_PADDING = 4f
internal const val CAMERA_PRO_BAND = 104f
private const val CAMERA_BASELINE_GAP = 4f
private const val CAMERA_ZOOM_BAND = 48f
internal const val CAMERA_LEFT_CLEARANCE = 60f

/** Geometry in dp, relative to the padded camera workspace, before any UI rotation. */
internal data class CameraCaptureBaseline(val frame: Rect, val dockStart: Float, val vertical: Boolean) {
    private val boundary get() = (minOf(if (vertical) frame.right else frame.bottom, dockStart) - CAMERA_BASELINE_GAP).coerceAtLeast(0f)
    fun pro(): Rect = band(boundary, CAMERA_PRO_BAND)
    fun zoom(pro: Boolean): Rect = band((boundary - if (pro) CAMERA_PRO_BAND else 0f).coerceAtLeast(0f), CAMERA_ZOOM_BAND)
    private fun band(end: Float, depth: Float): Rect = if (vertical) {
        Rect((end - depth).coerceAtLeast(0f), frame.top, end, frame.bottom)
    } else Rect(frame.left, (end - depth).coerceAtLeast(0f), frame.right, end)
}

internal val LocalCameraCaptureBaseline = compositionLocalOf<CameraCaptureBaseline?> { null }

internal fun cameraCaptureBaseline(width: Float, stageHeight: Float, vertical: Boolean, dockExtent: Float): CameraCaptureBaseline {
    val height = (stageHeight - 2 * STAGE_VERTICAL_PADDING).coerceAtLeast(0f)
    val photo = cameraCenteredCaptureWindow(width, height, if (vertical) 16f / 9f else 9f / 16f, vertical, 0f)
    val lastDockStart = ((if (vertical) width else height) - dockExtent).coerceAtLeast(0f)
    val dockStart = minOf(if (vertical) photo.right else photo.bottom, lastDockStart)
    return CameraCaptureBaseline(photo, dockStart, vertical)
}

internal fun CameraCaptureBaseline.dockPositionInStage(): Float = dockStart + if (vertical) 0f else STAGE_VERTICAL_PADDING

internal fun cameraCenteredCaptureWindow(width: Float, height: Float, ratio: Float, vertical: Boolean,
    fullscreenProgress: Float, sideInset: Float = CAMERA_PREVIEW_SIDE_INSET): Rect {
    val header = minOf(if (vertical) CAMERA_LEFT_CLEARANCE else CAMERA_HEADER_CLEARANCE,
        (if (vertical) width else height) / 2) * (1f - fullscreenProgress)
    val side = minOf(sideInset, width * .25f)
    val left = if (vertical) maxOf(header, side) else side
    val top = if (vertical) 0f else header
    val right = width - side
    val size = cameraPhotoWindow(right - left, height - top, ratio)
    val x = left + (right - left - size.width).coerceAtLeast(0f) * .5f
    val y = if (vertical) (height - size.height) * .5f else top
    return Rect(x, y, x + size.width, y + size.height)
}
