package com.kigtts.next.ui.cards.camera

import androidx.compose.runtime.*

/** Hardware capability and the displayed zoom survive a transient camera rebind together. */
internal class CameraZoomState {
    var ratio by mutableFloatStateOf(1f)
    var minimum by mutableFloatStateOf(1f); private set
    var maximum by mutableFloatStateOf(1f); private set
    private var frontLens: Boolean? = null

    fun attach(front: Boolean, min: Float, max: Float): Float {
        ratio = (if (frontLens == front) ratio else 1f).coerceIn(min, max)
        minimum = min; maximum = max; frontLens = front
        return ratio
    }

    fun observed(value: Float, min: Float, max: Float, restoring: Boolean) {
        minimum = min; maximum = max
        if (!restoring) ratio = value.coerceIn(min, max)
    }

    fun clear() { ratio = 1f; minimum = 1f; maximum = 1f; frontLens = null }
}
