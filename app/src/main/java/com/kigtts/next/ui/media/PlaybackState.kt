package com.kigtts.next.ui.media

import androidx.compose.runtime.*
import androidx.media3.common.Player

@Stable
class PlaybackState {
    var player:Player? by mutableStateOf(null)
    var playing by mutableStateOf(false)
    var rendered by mutableStateOf(false)
    var position by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(1f)
    var scrubbing by mutableStateOf(false)
    var muted by mutableStateOf(false)
    var allowed by mutableStateOf(true)
    var interaction by mutableIntStateOf(0); private set
    private var resumeAfterScrub = false
    fun wake() { interaction++ }
    fun toggle() {
        if (!allowed || scrubbing) return
        val current = player ?: return
        wake()
        if (current.playbackState == Player.STATE_ENDED) { current.seekTo(0); current.play() }
        else if (current.playWhenReady) current.pause() else current.play()
    }
    fun seek(value: Float) {
        if (!allowed) return
        val current = player ?: return
        if (!scrubbing) {
            resumeAfterScrub = current.playWhenReady && current.playbackState != Player.STATE_ENDED
            scrubbing = true
            current.pause()
        }
        wake()
        // Avoid a one-shot completion event while the thumb is held at the right edge.
        position = value.coerceIn(0f, (duration - 1f).coerceAtLeast(0f))
        current.seekTo(position.toLong())
    }
    fun finishScrub() {
        if (!scrubbing) return
        scrubbing = false; wake()
        if (allowed && resumeAfterScrub) player?.play()
        resumeAfterScrub = false
    }
    fun cancelScrub() { scrubbing = false; resumeAfterScrub = false }
}
