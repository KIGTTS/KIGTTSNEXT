package com.kigtts.next.ui.cards.camera

import android.graphics.ColorMatrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*

internal class CameraPreviewFilter(initial: FloatArray) {
    val clock = Animatable(0f)
    var from = initial
    var target = initial
    var start = 0f
    var end = 0f
    fun values(): FloatArray {
        val phase = if (end <= start) 1f else ((clock.value - start) / (end - start)).coerceIn(0f, 1f)
        return FloatArray(20) { from[it] + (target[it] - from[it]) * phase }
    }
    fun matrix() = ColorMatrix(values())
}

/** Preserve the currently painted colors on reversals; no snap-to-zero frame and no photo-size invalidation. */
@Composable
internal fun rememberCameraPreviewFilter(filter: PhotoFilter, strength: Float): CameraPreviewFilter {
    val target = remember(filter, strength) { PhotoFilters.coefficients(filter, strength) }
    val state = remember { CameraPreviewFilter(target) }
    LaunchedEffect(target) {
        if (!state.target.contentEquals(target)) {
            val current = state.values()
            state.from = current; state.target = target
            state.start = state.clock.value; state.end = state.start + 1f
            state.clock.animateTo(state.end, tween(160))
        }
    }
    return state
}
