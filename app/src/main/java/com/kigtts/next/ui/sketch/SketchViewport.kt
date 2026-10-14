package com.kigtts.next.ui.sketch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/** The same uniform mapping is used for rendering, drawing samples and two-finger navigation. */
internal data class SketchViewport(val document: IntSize, val window: IntSize, val zoom: Float, val pan: Offset) {
    val scale = minOf(window.width.toFloat() / document.width.coerceAtLeast(1),
        window.height.toFloat() / document.height.coerceAtLeast(1)).coerceAtLeast(.0001f) * zoom
    val origin = Offset((window.width - document.width * scale) / 2f,
        (window.height - document.height * scale) / 2f) + pan * scale
    fun toDocument(point: Offset) = (point - origin) / scale
}
