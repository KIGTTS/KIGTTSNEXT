package com.kigtts.next

import androidx.media3.common.*
import java.lang.reflect.Proxy

/** Controllable real Player interface; tests drive completion/focus events without an audio device. */
internal class AudioPlayerFixture {
    private val listeners = mutableListOf<Player.Listener>()
    var position = 0L; var playing = false; var state = Player.STATE_IDLE
    var prepares = 0; var released = 0; var item: MediaItem? = null
    val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, arguments ->
        val args = arguments.orEmpty()
        when (method.name) {
            "addListener" -> { listeners += args[0] as Player.Listener; null }
            "removeListener" -> { listeners -= args[0] as Player.Listener; null }
            "setMediaItem" -> { item = args[0] as MediaItem; position = 0; null }
            "prepare" -> { prepares++; ready(); null }
            "play" -> { playingEvent(true); null }
            "pause", "stop" -> { playingEvent(false); null }
            "seekTo" -> { position = (args.last() as Number).toLong(); if (state == Player.STATE_ENDED) ready(); null }
            "release" -> { released++; null }
            "getDuration" -> 3000L
            "getCurrentPosition" -> position
            "getPlaybackState" -> state
            "isPlaying" -> playing
            "getVolume" -> 1f
            "hashCode" -> System.identityHashCode(this)
            "equals" -> args.firstOrNull() === proxy
            "toString" -> "AudioPlayerFixture"
            else -> when (method.returnType) { Boolean::class.javaPrimitiveType -> false; Int::class.javaPrimitiveType -> 0; Long::class.javaPrimitiveType -> 0L; Float::class.javaPrimitiveType -> 0f; else -> null }
        }
    } as Player
    private fun ready() { state = Player.STATE_READY; listeners.toList().forEach { it.onPlaybackStateChanged(state) } }
    fun playingEvent(value: Boolean) { playing = value; listeners.toList().forEach { it.onIsPlayingChanged(value) } }
    fun end() { position = 3000L; playingEvent(false); state = Player.STATE_ENDED; listeners.toList().forEach { it.onPlaybackStateChanged(state) } }
}
