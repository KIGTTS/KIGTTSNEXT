package com.kigtts.next.ui.cards.camera

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

internal data class CameraZoomDialGeometry(val radius: Float, val center: Offset, val origin: Float)

/** Anchor to the photograph: expose only a shallow arc, including in square/narrow crops. */
internal fun cameraZoomDialGeometry(size: Size, vertical: Boolean): CameraZoomDialGeometry {
    val cross = if (vertical) size.height else size.width
    val depth = if (vertical) size.width else size.height
    val radius = minOf(cross * .62f, depth * .64f)
    return if (vertical) CameraZoomDialGeometry(radius,
        Offset(size.width + radius * .5f, size.height / 2f), 180f)
    else CameraZoomDialGeometry(radius,
        Offset(size.width / 2f, size.height + radius * .5f), 270f)
}
