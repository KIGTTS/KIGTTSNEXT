package com.kigtts.next.ui.media

import androidx.compose.runtime.*

/** Shared by the picture and optional controls outside an editor preview. */
@Stable
class LivePhotoState {
    val playback = PlaybackState()
    var requested by mutableStateOf(false); private set
    var mounted by mutableStateOf(false); internal set
    private var started = false
    fun autoStart(enabled: Boolean, active: Boolean, available: Boolean) {
        if (enabled && active && available && !started) start()
    }
    fun start() {
        started = true; playback.wake()
        if (mounted) { playback.player?.seekTo(0); playback.player?.play() }
        else { playback.rendered = false; mounted = true }
        requested = true
    }
    fun close() { playback.cancelScrub(); playback.player?.pause(); requested = false; playback.wake() }
}
