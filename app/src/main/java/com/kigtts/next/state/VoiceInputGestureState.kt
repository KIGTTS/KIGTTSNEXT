package com.kigtts.next.state

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

enum class VoiceReleaseTarget { SEND, READ, CANCEL, CONTINUOUS, PLUGIN }

/** Retains the initiating touch while the overlay is inserted into the existing page. */
@Stable
class VoiceInputGestureState {
    var point by mutableStateOf<Offset?>(null); private set
    var touching by mutableStateOf(false); private set
    var armed by mutableStateOf(false); private set
    var pendingRelease by mutableStateOf<VoiceReleaseTarget?>(null); private set
    private val areas = mutableStateMapOf<VoiceReleaseTarget, Rect>()
    private val pluginAreas = mutableStateMapOf<String, Rect>()
    private var anchor: Offset? = null
    private var minimumMove = 0f
    private var aimed by mutableStateOf(false)
    private var request = 0L
    var pendingPluginAction: String? by mutableStateOf(null); private set
    val selectedPluginAction: String? get() = point?.takeIf { aimed }?.let { p -> pluginAreas.entries.firstOrNull { it.value.contains(p) }?.key }
    fun pluginArea(id: String, bounds: Rect) { pluginAreas[id] = bounds }
    fun removePluginArea(id: String) { pluginAreas.remove(id) }
    fun consumePluginRelease() = pendingPluginAction.also { pendingPluginAction = null }
    val selected: VoiceReleaseTarget get() = if (selectedPluginAction != null) VoiceReleaseTarget.PLUGIN else point?.takeIf { aimed }?.let { position ->
        areas.entries.firstOrNull { it.value.contains(position) }?.key
    } ?: VoiceReleaseTarget.SEND
    fun area(target: VoiceReleaseTarget, bounds: Rect) { areas[target] = bounds }
    fun removeArea(target: VoiceReleaseTarget) { areas.remove(target) }
    fun down(position: Offset, touchSlop: Float = 0f) { touching = true; point = position; minimumMove = touchSlop; armed = false; aimed = false }
    fun arm(): Long {
        discardRelease(); anchor = point; armed = touching; aimed = false
        return ++request
    }
    fun move(position: Offset) {
        if (!touching) return
        if (armed && !aimed && anchor?.let { (position - it).getDistance() > minimumMove } == true) aimed = true
        point = position
    }
    fun release() {
        if (armed) { pendingRelease = selected; pendingPluginAction = selectedPluginAction }
        touching = false; armed = false; point = null
    }
    fun cancel() { if (armed) pendingRelease = VoiceReleaseTarget.CANCEL; touching = false; armed = false; point = null }
    fun consumeRelease(): VoiceReleaseTarget? = pendingRelease.also { pendingRelease = null }
    fun discardRelease() { pendingRelease = null; pendingPluginAction = null }
    fun reject(requestId: Long) { if (requestId == request) { discardRelease(); armed = false } }
}
