package com.kigtts.next.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp

internal class LedPlayback {
    var offset by mutableFloatStateOf(0f)
        private set
    private var period = 0f
    private var loop = true
    private var right = false
    private var dragging = false
    private var velocity = 0f
    private var pause = 1.2f

    fun layout(range: Float, loop: Boolean, right: Boolean) {
        if (range == period && loop == this.loop && right == this.right) return
        period = range
        this.loop = loop
        this.right = right
        offset = normalize(offset)
        if (period <= 0f) { dragging = false; velocity = 0f }
    }

    fun restart() {
        offset = 0f
        dragging = false
        velocity = 0f
        pause = 1.2f
    }

    fun startScrub(): Boolean {
        if (period <= 0f) return false
        dragging = true
        velocity = 0f
        return true
    }

    fun scrub(deltaX: Float) {
        if (dragging) offset = normalize(offset + if (right) deltaX else -deltaX)
    }

    fun finishScrub(velocityX: Float) {
        if (!dragging) return
        dragging = false
        velocity = (if (right) velocityX else -velocityX).coerceIn(-5500f, 5500f)
        pause = .85f
    }

    fun tick(dt: Float, automaticSpeed: Float) {
        if (dragging || period <= 0f) return
        if (abs(velocity) >= 12f) {
            val decay = exp(-6f * dt)
            val intended = offset + velocity * (1f - decay) / 6f
            offset = normalize(intended)
            velocity = if (!loop && intended != offset) 0f else velocity * decay
        } else if (pause > 0f) {
            pause = (pause - dt).coerceAtLeast(0f)
        } else if (!loop && offset >= period) {
            offset = 0f
            pause = .8f
        } else {
            offset = normalize(offset + dt * automaticSpeed)
            if (!loop && offset >= period) pause = .8f
        }
    }

    fun page(step: Float) {
        if (dragging || period <= 0f) return
        offset = if (offset >= period) 0f else (offset + step).coerceAtMost(period)
    }

    private fun normalize(value: Float): Float = when {
        period <= 0f -> 0f
        !loop -> value.coerceIn(0f, period)
        else -> (value % period).let { if (it < 0f) it + period else it }
    }
}
