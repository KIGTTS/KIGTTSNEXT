@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.ui.media

import android.view.TextureView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import com.kigtts.next.media.MediaScale
import com.kigtts.next.ui.navigation.LocalPageActive
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

@Composable
fun MediaVideo(file:File?,description:String,once:Boolean,ended:()->Unit,modifier:Modifier=Modifier,
    scale:MediaScale=MediaScale.FIT,playback:PlaybackState=remember { PlaybackState() },showControls:Boolean=true,autoPlay:Boolean=once) {
    if(file==null) { Box(modifier) { Text("视频资源不可用") };return }
    val context=LocalContext.current;val active=LocalPageActive.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val onEnded by rememberUpdatedState(ended)
    val effect=remember(file.path) { LetterboxBlurEffect() }
    SideEffect { effect.scale=scale }
    val player=remember(file.path) { ExoPlayer.Builder(context).build().apply {
        setVideoEffects(listOf(effect));setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        volume=if(once)0f else 1f;setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
        prepare()
    } }
    var error by remember(player) { mutableStateOf<String?>(null) }
    var viewport by remember(player) { mutableStateOf(IntSize.Zero) }
    DisposableEffect(player,lifecycle) {
        playback.player=player;playback.muted=once;playback.rendered=false
        val listener=object:Player.Listener {
            override fun onIsPlayingChanged(playing:Boolean) { playback.playing=playing }
            override fun onRenderedFirstFrame() { playback.rendered=true }
            override fun onPlaybackStateChanged(state:Int) {
                playback.duration=player.duration.takeIf { it>0 }?.toFloat() ?: 1f
                if(state==Player.STATE_ENDED&&once)onEnded()
            }
            override fun onPlayerError(exception:PlaybackException) { error="无法播放此视频："+exception.errorCodeName }
        }
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP)player.pause() }
        lifecycle.addObserver(observer);player.addListener(listener)
        onDispose { lifecycle.removeObserver(observer);player.removeListener(listener);player.release();if(playback.player===player)playback.player=null }
    }
    LaunchedEffect(active) { if(!active) { player.pause();if(once)onEnded() } }
    LaunchedEffect(player,autoPlay) { if(autoPlay&&active)player.play() else player.pause() }
    LaunchedEffect(player,active) { if(active)snapshotFlow { playback.playing }.collectLatest { playing ->
        if(playing)while(true)withFrameNanos { if(!playback.scrubbing)playback.position=player.currentPosition.toFloat() }
        else if(!playback.scrubbing)playback.position=player.currentPosition.toFloat()
    } }
    LaunchedEffect(scale,viewport) { delay(80);if(playback.rendered&&!player.isPlaying)player.seekTo(player.currentPosition) }
    Box(modifier,contentAlignment=Alignment.Center) {
        key(player) { AndroidView(factory={ context -> TextureView(context).apply {
            isOpaque=false
            // Observe the actual decoder surface, after its native layout, rather than the
            // Compose parent's size before AndroidView has caught up with the window resize.
            addOnLayoutChangeListener { _,left,top,right,bottom,_,_,_,_ ->
                val width=right-left;val height=bottom-top
                if(width>0&&height>0) { viewport=IntSize(width,height);effect.aspect=width.toFloat()/height }
            }
            player.setVideoTextureView(this)
        } },update={ it.contentDescription=description },modifier=Modifier.fillMaxSize(),onRelease={
            if(playback.player===player)player.clearVideoTextureView(it)
        }) }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        if(showControls)PlaybackControls(playback,true,once,{ player.play() },onEnded,
            Modifier.align(Alignment.BottomCenter).padding(bottom=52.dp))
    }
}
