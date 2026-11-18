@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.ui.media

import com.kigtts.next.i18n.uiText

import android.view.TextureView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clipToBounds
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.ScrubbingModeParameters
import com.kigtts.next.media.MediaScale
import com.kigtts.next.ui.navigation.LocalPageActive
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

internal val LocalVideoPlayerFactory = staticCompositionLocalOf<(android.content.Context) -> ExoPlayer> {
    { ExoPlayer.Builder(it).build() }
}

@Composable
fun MediaVideo(file:File?,description:String,once:Boolean,ended:()->Unit,modifier:Modifier=Modifier,
    scale:MediaScale=MediaScale.FIT,playback:PlaybackState=remember { PlaybackState() },showControls:Boolean=true,autoPlay:Boolean=once,
    playAudio:Boolean=!once,onLongPress:(()->Unit)?=null) {
    if(file==null) { Box(modifier) { Text(uiText("media.video_unavailable")) };return }
    val context=LocalContext.current;val active=LocalPageActive.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val onEnded by rememberUpdatedState(ended)
    val hold by rememberUpdatedState(onLongPress)
    val backdrop=remember(file.path) { VideoBackdropTarget() }
    val effect=remember(file.path) { LetterboxBlurEffect(backdrop) }
    val factory=LocalVideoPlayerFactory.current
    val player=remember(file.path) { factory(context).apply {
        setVideoEffects(listOf(effect));setSeekParameters(SeekParameters.EXACT)
        setScrubbingModeParameters(ScrubbingModeParameters.Builder().setFractionalSeekTolerance(null,null).build())
        volume=if(playAudio)1f else 0f;setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
        setHandleAudioBecomingNoisy(true)
    } }
    val gate=remember(player) { VideoPlaybackGate(playback) }
    val visible by rememberUpdatedState(active);val automatic by rememberUpdatedState(autoPlay)
    var error by remember(player) { mutableStateOf<String?>(null) }
    var sourceSize by remember(player) { mutableStateOf(IntSize(640,640)) }
    DisposableEffect(player,lifecycle) {
        playback.player=player;playback.muted=!playAudio;playback.rendered=false
        val listener=object:Player.Listener {
            override fun onIsPlayingChanged(playing:Boolean) { playback.playing=playing }
            override fun onRenderedFirstFrame() { playback.rendered=true }
            override fun onPlaybackStateChanged(state:Int) {
                playback.duration=player.duration.takeIf { it>0 }?.toFloat() ?: 1f
                if(state==Player.STATE_ENDED&&once&&!playback.scrubbing)onEnded()
            }
            override fun onPlayerError(exception:PlaybackException) { error="无法播放此视频："+exception.errorCodeName }
        }
        val observer=LifecycleEventObserver { _,_ -> gate.update(visible&&lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),automatic) }
        lifecycle.addObserver(observer);player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        if(playback.position>0f)player.seekTo(playback.position.toLong())
        player.prepare()
        onDispose {
            if(playback.allowed)playback.resumeWhenVisible=player.playWhenReady&&player.playbackState!=Player.STATE_ENDED&&!playback.scrubbing
            playback.position=player.currentPosition.toFloat();playback.allowed=false;playback.playing=false
            playback.cancelScrub();lifecycle.removeObserver(observer);player.removeListener(listener);player.release()
            backdrop.release();if(playback.player===player)playback.player=null
        }
    }
    SideEffect { gate.update(active&&lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),autoPlay) }
    // With video effects Media3 1.11.1 suppresses Player.onVideoSizeChanged. Use the
    // processed frame's actual dimensions, including rotation/pixel aspect and reduction.
    LaunchedEffect(effect) { effect.outputSize.filterNotNull().collect { sourceSize=it } }
    LaunchedEffect(player,playAudio) { playback.muted=!playAudio;player.volume=if(playAudio)1f else 0f }
    LaunchedEffect(player,active) { if(active)snapshotFlow { playback.playing }.collectLatest { playing ->
        if(playing)while(true)withFrameNanos { if(!playback.scrubbing)playback.position=player.currentPosition.toFloat() }
        else if(!playback.scrubbing)playback.position=player.currentPosition.toFloat()
    } }
    Box(modifier.clipToBounds().testTag("media-video-frame")
        .semantics { if(playback.allowed)onClick(if(playback.playing)"暂停"else"播放") { playback.toggle();true } },contentAlignment=Alignment.Center) {
        // The backdrop shares the fixed source raster. Cover scaling happens atomically with
        // foreground layout, rather than waiting for the GL producer to catch up to a resize.
        VideoBackdropView(backdrop,Modifier.fillMaxSize().videoFrameLayout(sourceSize,MediaScale.FILL)
            .testTag("media-video-backdrop"),playback::toggle) { hold?.let { it();true } ?: false }
        key(player) { AndroidView(factory={ context -> TextureView(context).apply {
            isOpaque=false
            // TextureView owns native touch dispatch once the decoder surface is attached.
            // Handle taps there instead of competing with AndroidView's interop gesture node.
            setOnClickListener { playback.toggle() }
            setOnLongClickListener { hold?.let { it();true } ?: false }
            player.setVideoTextureView(this)
        } },update={ it.contentDescription=description },modifier=Modifier.fillMaxSize().videoFrameLayout(sourceSize,scale).testTag("media-video-foreground"),onRelease={
            if(playback.player===player)player.clearVideoTextureView(it)
        }) }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        if(showControls)PlaybackControls(playback,true,once,{ player.play() },onEnded,
            Modifier.align(Alignment.BottomCenter).padding(bottom=52.dp))
    }
}
