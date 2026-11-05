package com.kigtts.next.ui.cards.camera

import androidx.compose.ui.geometry.Size
import com.kigtts.plugin.api.PolaroidFormat
import kotlin.math.roundToInt

internal fun nextCameraFormat(formats: List<PolaroidFormat>, current: PolaroidFormat): PolaroidFormat =
    formats.getOrNull((formats.indexOf(current) + 1).mod(formats.size.coerceAtLeast(1))) ?: current

internal fun cameraPhotoWindow(width: Float, height: Float, ratio: Float): Size {
    if (!width.isFinite() || !height.isFinite() || !ratio.isFinite() || width <= 0 || height <= 0 || ratio <= 0) return Size.Zero
    val fittedWidth = minOf(width, height * ratio)
    // Float multiplication/division can exceed the limit by one ULP; never grow outside the window.
    return Size(fittedWidth, minOf(height, fittedWidth / ratio))
}

internal fun cameraSideControls(width: Float, height: Float) = width > height * 1.25f

internal fun cameraOverlaysShutter(width: Float, height: Float, photoAspect: Float?): Boolean {
    if (width <= 0f || height <= 0f || photoAspect == null || !photoAspect.isFinite()) return false
    val relative = photoAspect / (width / height)
    return relative in .85f..(1f / .85f)
}

internal fun cameraExposureAt(y: Float, height: Float, range: IntRange): Int {
    if (height <= 0 || !height.isFinite() || !y.isFinite() || range.isEmpty()) return 0
    return (range.last - (y / height).coerceIn(0f, 1f) * (range.last - range.first)).roundToInt().coerceIn(range)
}
