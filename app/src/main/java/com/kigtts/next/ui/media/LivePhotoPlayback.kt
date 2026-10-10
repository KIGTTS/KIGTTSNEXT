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
fun BoxScope.LivePhotoPlayback(file:File?,name:String,scale:MediaScale,autoPlay:Boolean=false) {
    var requested by remember { mutableStateOf(false) };var mounted by remember { mutableStateOf(false) }
    val playback=remember { PlaybackState() };val alpha=remember { Animatable(0f) }
    val active=LocalPageActive.current
    LaunchedEffect(autoPlay) {
        if(autoPlay&&active) { playback.rendered=false; mounted=true;requested=true }
        else if(!autoPlay)requested=false
    }
    LaunchedEffect(requested,playback.rendered) {
        if(requested&&playback.rendered)alpha.animateTo(1f,tween(180,easing=FastOutSlowInEasing))
        else if(!requested) { playback.player?.pause();alpha.animateTo(0f,tween(160));mounted=false }
    }
    if(mounted)MediaVideo(file,name,true,{ requested=false },Modifier.fillMaxSize().graphicsLayer { this.alpha=alpha.value },
        scale,playback,showControls=false)
    PlaybackControls(playback,requested,true,start={
        if(mounted) { playback.player?.seekTo(0);playback.player?.play() } else { playback.rendered=false;mounted=true }
        requested=true
    },close={ requested=false },
        modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=52.dp))
}
