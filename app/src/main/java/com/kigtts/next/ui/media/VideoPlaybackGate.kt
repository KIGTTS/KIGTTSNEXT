package com.kigtts.next.ui.media

/** Defer the first autoplay until visible. Suspend playback without ending a live photo. */
internal class VideoPlaybackGate(private val state: PlaybackState) {
    private var previousAllowed: Boolean? = null
    private var previousAutoPlay: Boolean? = null
    private var pending = false
    fun update(allowed: Boolean, autoPlay: Boolean) {
        val player = state.player ?: return
        if (previousAutoPlay != autoPlay) {
            pending = autoPlay && (previousAutoPlay != null || !state.autoPlayConsumed)
            if (!autoPlay && previousAutoPlay != null) { state.resumeWhenVisible = false; player.pause() }
            previousAutoPlay = autoPlay
        }
        if (!allowed && previousAllowed == true) {
            state.resumeWhenVisible = player.playWhenReady && player.playbackState != androidx.media3.common.Player.STATE_ENDED && !state.scrubbing
            state.cancelScrub(); player.pause()
        }
        state.allowed = allowed
        if (allowed && (pending || previousAllowed != true && state.resumeWhenVisible)) {
            pending = false; state.resumeWhenVisible = false; state.autoPlayConsumed = true; player.play()
        }
        previousAllowed = allowed
    }
}
