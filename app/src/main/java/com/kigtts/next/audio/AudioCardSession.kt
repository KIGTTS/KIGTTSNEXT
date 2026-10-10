package com.kigtts.next.audio

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import com.kigtts.next.ui.media.PlaybackState

/** Owned by the ViewModel, so rotation does not create another decoder or restart the file. */
class AudioCardSession(private val createPlayer: () -> Player) {
    constructor(context: Context) : this({ ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        setHandleAudioBecomingNoisy(true)
    } })
    val playback = PlaybackState()
    var expanded by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private var source: String? = null
    private var uri: Uri? = null
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { playback.playing = isPlaying }
        override fun onPlaybackStateChanged(state: Int) {
            playback.duration = playback.player?.duration?.takeIf { it > 0 }?.toFloat() ?: 1f
            if (state == Player.STATE_ENDED) { playback.playing = false; expanded = false }
        }
        override fun onPlayerError(error: PlaybackException) { this@AudioCardSession.error = "音频暂时无法播放"; expanded = false }
    }
    fun start(id: String, location: Uri, autoPlay: Boolean = true) {
        val player = playback.player ?: createPlayer().also { playback.player = it; it.addListener(listener) }
        error = null
        if (source != id || uri != location) {
            source = id; uri = location; playback.position = 0f; playback.duration = 1f
            player.setMediaItem(MediaItem.fromUri(location)); player.prepare()
        } else player.seekTo(0)
        expanded = autoPlay; if(autoPlay)player.play() else player.pause()
    }
    fun restart() { val id = source ?: return; val location = uri ?: return; start(id, location) }
    fun pause() { playback.player?.pause() }
    fun collapse() { playback.player?.pause(); expanded = false }
    fun clear() { playback.player?.stop(); playback.playing = false; expanded = false; source = null; uri = null; error = null }
    fun release() { playback.player?.let { it.removeListener(listener); it.release() }; playback.player = null }
}
