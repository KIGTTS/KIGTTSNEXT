@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick

/** The same pill expands into controls; only the seek bar reads per-frame progress. */
@Composable
fun PlaybackControls(state:PlaybackState,expanded:Boolean,once:Boolean,start:()->Unit,close:()->Unit,modifier:Modifier=Modifier) {
    val phase by animateFloatAsState(if(expanded)1f else 0f,MaterialTheme.motionScheme.fastSpatialSpec(),label="live-controls-morph")
    val visible=phase.coerceIn(0f,1f)
    Surface(modifier.fillMaxWidth(if(once).34f+.62f*visible else .96f),shape=CircleShape,
        color=MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha=.94f)) {
        Row(Modifier.height(48.dp).padding(horizontal=4.dp).clipToBounds(),verticalAlignment=Alignment.CenterVertically) {
            // One play button survives the entire morph; the label yields its width to the track.
            TextButton(feedbackClick {
                if(!expanded)start() else { val player=state.player ?: return@feedbackClick
                    if(player.playbackState==androidx.media3.common.Player.STATE_ENDED)player.seekTo(0)
                    if(player.isPlaying)player.pause() else player.play() }
            },Modifier.width(48.dp+64.dp*(1f-visible)).then(if(!expanded)Modifier.testTag("play-motion-photo") else Modifier),
                contentPadding=PaddingValues(4.dp)) {
                Icon(if(state.playing&&expanded)Icons.Default.Pause else Icons.Default.PlayArrow,if(!expanded)"播放实况" else if(state.playing)"暂停" else "播放")
                Box(Modifier.width(64.dp*(1f-visible)).clipToBounds().graphicsLayer { alpha=1f-visible }) { Text("播放实况",maxLines=1,softWrap=false) }
            }
            if(visible>0f)Row(Modifier.weight(1f).clipToBounds().graphicsLayer { alpha=visible },verticalAlignment=Alignment.CenterVertically) {
                WaveSeekBar(state,Modifier.weight(1f),expanded)
                IconButton(feedbackClick { state.muted=!state.muted;state.player?.volume=if(state.muted)0f else 1f },
                    Modifier.width(48.dp*visible),enabled=expanded) {
                    Icon(if(state.muted)Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,"切换视频声音") }
                if(once)IconButton(feedbackClick(close),Modifier.width(48.dp*visible),enabled=expanded) { Icon(Icons.Default.Close,"返回照片") }
            }
        }
    }
}

@Composable
private fun WaveSeekBar(state:PlaybackState,modifier:Modifier,expanded:Boolean) {
    val duration=state.duration.coerceAtLeast(1f)
    Slider(state.position.coerceIn(0f,duration),{ state.scrubbing=true;state.position=it },modifier.testTag("media-wave-seek"),
        valueRange=0f..duration,enabled=expanded&&state.player!=null&&state.duration>1f,
        onValueChangeFinished={ state.player?.seekTo(state.position.toLong());state.scrubbing=false },
        track={ slider -> MediaWaveTrack(progress={ (slider.value/duration).coerceIn(0f,1f) },playing=state.playing) })
}
