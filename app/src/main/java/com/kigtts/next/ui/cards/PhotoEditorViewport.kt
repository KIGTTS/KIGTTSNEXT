package com.kigtts.next.ui.cards

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Review transform only; neither the crop nor sticker coordinates depend on the viewport. */
@Stable
internal class PhotoEditorViewport {
    var scale by mutableFloatStateOf(1f); private set
    var translation by mutableStateOf(Offset.Zero); private set

    fun transform(center: Offset, pivot: Offset, pan: Offset, zoom: Float, width: Float, height: Float) {
        val target = (scale * zoom).coerceIn(1f, 5f)
        val factor = target / scale
        val delta = translation * factor + (center - pivot) * (1f - factor) + pan
        scale = target
        translation = if (target == 1f) Offset.Zero else Offset(
            delta.x.coerceIn(-width * target / 2f, width * target / 2f),
            delta.y.coerceIn(-height * target / 2f, height * target / 2f))
    }
}

internal fun Modifier.photoEditorViewport(state: PhotoEditorViewport, pivot: Offset): Modifier = pointerInput(state, pivot) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var transforming = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val center = event.calculateCentroid(useCurrent = false)
                if (center != Offset.Unspecified) {
                    state.transform(center, pivot, event.calculatePan(), event.calculateZoom(), size.width.toFloat(), size.height.toFloat())
                }
                transforming = true
            }
            if (transforming) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
