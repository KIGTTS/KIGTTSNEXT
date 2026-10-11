package com.kigtts.next.ui.media

/** Defer the first autoplay until visible. Suspend playback without ending a live photo. */
internal class VideoPlaybackGate(private val state: PlaybackState) {
    private var previousAllowed: Boolean? = null
    private var previousAutoPlay: Boolean? = null
    private var pending = false
    private var resume = false
    fun update(allowed: Boolean, autoPlay: Boolean) {
        val player = state.player ?: return
        if (previousAutoPlay != autoPlay) {
            pending = autoPlay
            if (!autoPlay && previousAutoPlay != null) { resume = false; player.pause() }
            previousAutoPlay = autoPlay
        }
        if (!allowed && previousAllowed == true) {
            resume = player.playWhenReady && !state.scrubbing
            state.cancelScrub(); player.pause()
        }
        state.allowed = allowed
        if (allowed && (pending || previousAllowed == false && resume)) {
            pending = false; resume = false; player.play()
        }
        previousAllowed = allowed
    }
}
