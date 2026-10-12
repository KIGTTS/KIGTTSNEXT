package com.kigtts.next

import androidx.media3.common.*
import java.lang.reflect.Proxy

/** Controllable real Player interface; tests drive completion/focus events without an audio device. */
internal class AudioPlayerFixture(interfaces:Array<Class<*>> = arrayOf(Player::class.java)) {
    private val listeners = mutableListOf<Player.Listener>()
    var position = 0L; var playing = false; var state = Player.STATE_IDLE
    var prepares = 0; var released = 0; var item: MediaItem? = null
    var playWhenReady=false; var volume=1f;val seeks=mutableListOf<Long>()
    var seekParameters:Any?=null
    var redraws=0
    var scrubMode=false
    var videoView:android.view.TextureView?=null
    var videoEffects:List<androidx.media3.common.Effect> = emptyList()
    val player = Proxy.newProxyInstance(Player::class.java.classLoader, interfaces) { proxy, method, arguments ->
        val args = arguments.orEmpty()
        when (method.name) {
            "addListener" -> { listeners += args[0] as Player.Listener; null }
            "removeListener" -> { listeners -= args[0] as Player.Listener; null }
            "setMediaItem" -> { item = args[0] as MediaItem; position = 0; null }
            "prepare" -> { prepares++; ready(); null }
            "play" -> { playingEvent(true); null }
            "pause", "stop" -> { playingEvent(false); null }
            "seekTo" -> { position = (args.last() as Number).toLong();seeks+=position; if (state == Player.STATE_ENDED) ready(); null }
            "setSeekParameters" -> { seekParameters=args[0];null }
            "setVideoEffects" -> {
                if(args[0]==androidx.media3.common.VideoFrameProcessor.REDRAW)redraws++
                else { @Suppress("UNCHECKED_CAST") val effects=args[0] as List<androidx.media3.common.Effect>;videoEffects=effects }
                null
            }
            "setScrubbingModeEnabled" -> { scrubMode=args[0] as Boolean;null }
            "setVideoTextureView" -> { videoView=args[0] as android.view.TextureView;null }
            "setVolume" -> { volume=args[0] as Float;null }
            "release" -> { released++; null }
            "getDuration" -> 3000L
            "getCurrentPosition" -> position
            "getPlaybackState" -> state
            "isPlaying" -> playing
            "getPlayWhenReady" -> playWhenReady
            "getVolume" -> volume
            "hashCode" -> System.identityHashCode(this)
            "equals" -> args.firstOrNull() === proxy
            "toString" -> "AudioPlayerFixture"
            else -> when (method.returnType) { Boolean::class.javaPrimitiveType -> false; Int::class.javaPrimitiveType -> 0; Long::class.javaPrimitiveType -> 0L; Float::class.javaPrimitiveType -> 0f; else -> null }
        }
    } as Player
    private fun ready() { state = Player.STATE_READY; listeners.toList().forEach { it.onPlaybackStateChanged(state) } }
    fun playingEvent(value: Boolean) { playWhenReady=value;playing = value; listeners.toList().forEach { it.onIsPlayingChanged(value) } }
    fun firstFrame() { listeners.toList().forEach { it.onRenderedFirstFrame() } }
    fun videoSize(width:Int,height:Int) { listeners.toList().forEach { it.onVideoSizeChanged(VideoSize(width,height)) } }
    fun processedSize(width:Int,height:Int) { (videoEffects.single() as com.kigtts.next.ui.media.LetterboxBlurEffect).outputSize.value=androidx.compose.ui.unit.IntSize(width,height) }
    fun end() { position = 3000L; playingEvent(false); state = Player.STATE_ENDED; listeners.toList().forEach { it.onPlaybackStateChanged(state) } }
}
