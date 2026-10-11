package com.kigtts.next.ui.media

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.MediaScale
import java.io.File
import com.kigtts.next.ui.navigation.LocalPageActive

/** The photo stays mounted below the video, including decoder startup and the entire exit fade. */
@Composable
fun BoxScope.LivePhotoPlayback(file:File?,name:String,scale:MediaScale,autoPlay:Boolean=false,
    state:LivePhotoState=remember { LivePhotoState() },showControls:Boolean=true,playAudio:Boolean=false,onLongPress:(()->Unit)?=null) {
    val playback=state.playback;val alpha=remember { Animatable(0f) }
    val active=LocalPageActive.current
    LaunchedEffect(autoPlay,active,file) { state.autoStart(autoPlay,active,file!=null) }
    LaunchedEffect(state.requested,playback.rendered) {
        if(state.requested&&playback.rendered)alpha.animateTo(1f,tween(180,easing=FastOutSlowInEasing))
        else if(!state.requested) { playback.player?.pause();alpha.animateTo(0f,tween(160));state.mounted=false }
    }
    if(state.mounted)MediaVideo(file,name,true,state::close,Modifier.fillMaxSize().graphicsLayer { this.alpha=alpha.value },
        scale,playback,showControls=false,autoPlay=state.requested,playAudio=playAudio,onLongPress=onLongPress)
    if(showControls)PlaybackControls(playback,state.requested,true,start=state::start,close=state::close,
        modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=52.dp))
}
