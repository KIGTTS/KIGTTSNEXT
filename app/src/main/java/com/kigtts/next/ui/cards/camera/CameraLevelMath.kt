package com.kigtts.next.ui.cards.camera

import android.view.Surface
import kotlin.math.*

internal data class CameraLevelReading(val rollDegrees: Float, val flatX: Float, val flatY: Float,
    val nearFlat: Boolean, val aligned: Boolean)

/** Sensor axes stay in the device's natural orientation, independent of the current display. */
internal fun cameraLevelReading(x: Float, y: Float, z: Float, rotation: Int): CameraLevelReading? {
    if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
    val length = sqrt(x * x + y * y + z * z)
    if (!length.isFinite() || length < 1f) return null
    val (horizontal, vertical) = when (rotation) {
        Surface.ROTATION_90 -> y to -x
        Surface.ROTATION_180 -> -x to -y
        Surface.ROTATION_270 -> -y to x
        else -> x to y
    }
    val flatX = horizontal / length; val flatY = vertical / length
    val nearFlat = hypot(flatX, flatY) < .28f
    val roll = Math.toDegrees(atan2(horizontal, vertical).toDouble()).toFloat()
    return CameraLevelReading(roll, flatX, flatY, nearFlat,
        if (nearFlat) hypot(flatX, flatY) <= .035f else abs(roll) <= 1.5f)
}
