package com.kigtts.next.ui.cards

import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize

/** Viewer-only geometry. Cropping and the saved PolaroidCard are never modified here. */
internal class PolaroidPreviewTransform {
    var zoom by mutableFloatStateOf(1f); private set
    var pan by mutableStateOf(Offset.Zero); private set
    var paperSize = IntSize.Zero
    var viewportSize = IntSize.Zero
    private val center get() = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    fun bounds(): Rect {
        val half = Offset(paperSize.width * zoom / 2f, paperSize.height * zoom / 2f)
        return Rect(center + pan - half, center + pan + half)
    }
    fun transform(centroid: Offset, delta: Offset, scale: Float) {
        if (!scale.isFinite() || scale <= 0f) return
        val next = (zoom * scale).coerceIn(1f, 4f)
        if (next <= 1.001f) { reset(); return }
        val relative = centroid - center
        val ratio = next / zoom
        val shifted = (pan - relative) * ratio + relative + delta
        val maxX = paperSize.width * (next - 1f) / 2f
        val maxY = paperSize.height * (next - 1f) / 2f
        pan = Offset(shifted.x.coerceIn(-maxX, maxX), shifted.y.coerceIn(-maxY, maxY))
        zoom = next
    }
    fun reset() { zoom = 1f; pan = Offset.Zero }
}

/** Input stays in the fixed viewer coordinate system while the complete paper transforms. */
@Composable
internal fun Modifier.polaroidPreviewGestures(state: PolaroidPreviewTransform, enabled: Boolean,
    onEmptyTap: () -> Unit, onSwipeUp: (() -> Unit)? = null): Modifier {
    val interactive = rememberUpdatedState(enabled)
    val emptyTap = rememberUpdatedState(onEmptyTap)
    val swipe = rememberUpdatedState(onSwipeUp)
    return pointerInput(state) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            var touchedPaper = state.bounds().contains(first.position)
            var moved = false
            var multiple = false
            var end = first.position
            do {
                val event = awaitPointerEvent()
                end = event.changes.firstOrNull { it.id == first.id }?.position ?: end
                if (event.changes.count { it.pressed } > 1) {
                    multiple = true
                    if (event.changes.any { state.bounds().contains(it.position) }) touchedPaper = true
                }
                if ((event.changes.firstOrNull { it.id == first.id }?.position ?: first.position)
                    .minus(first.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (interactive.value && touchedPaper && event.changes.any { it.pressed } &&
                    (event.changes.count { it.pressed } > 1 || state.zoom > 1.001f)) {
                    state.transform(event.calculateCentroid(useCurrent = false), event.calculatePan(),
                        if (event.changes.count { it.pressed } > 1) event.calculateZoom() else 1f)
                    event.changes.forEach { if (it.position != it.previousPosition) it.consume() }
                }
            } while (event.changes.any { it.pressed })
            val travel = end - first.position
            if (interactive.value && touchedPaper && !multiple && state.zoom <= 1.001f &&
                travel.y < -72 * density && kotlin.math.abs(travel.x) < -travel.y * .6f) swipe.value?.invoke()
            if (interactive.value && !touchedPaper && !moved && !multiple) emptyTap.value()
        }
    }
}
