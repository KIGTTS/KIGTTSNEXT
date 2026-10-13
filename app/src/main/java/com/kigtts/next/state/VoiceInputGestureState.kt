package com.kigtts.next.state

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

enum class VoiceReleaseTarget { SEND, READ, CANCEL, CONTINUOUS }

/** Retains the initiating touch while the overlay is inserted into the existing page. */
@Stable
class VoiceInputGestureState {
    var point by mutableStateOf<Offset?>(null); private set
    var touching by mutableStateOf(false); private set
    var armed by mutableStateOf(false); private set
    var pendingRelease by mutableStateOf<VoiceReleaseTarget?>(null); private set
    private val areas = mutableStateMapOf<VoiceReleaseTarget, Rect>()
    val selected: VoiceReleaseTarget get() = point?.let { position ->
        areas.entries.firstOrNull { it.value.contains(position) }?.key
    } ?: VoiceReleaseTarget.SEND
    fun area(target: VoiceReleaseTarget, bounds: Rect) { areas[target] = bounds }
    fun removeArea(target: VoiceReleaseTarget) { areas.remove(target) }
    fun down(position: Offset) { touching = true; point = position; pendingRelease = null; armed = false }
    fun arm() { if (touching) armed = true }
    fun move(position: Offset) { if (touching) point = position }
    fun release() {
        if (armed) pendingRelease = selected
        touching = false; armed = false; point = null
    }
    fun cancel() { if (armed) pendingRelease = VoiceReleaseTarget.CANCEL; touching = false; armed = false; point = null }
    fun consumeRelease(): VoiceReleaseTarget? = pendingRelease.also { pendingRelease = null }
}
