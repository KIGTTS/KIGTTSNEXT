@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.media

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.expressivePillShapes

/** The same pill expands into controls; only the seek bar reads per-frame progress. */
@Composable
fun PlaybackControls(state:PlaybackState,expanded:Boolean,once:Boolean,start:()->Unit,close:()->Unit,modifier:Modifier=Modifier,
    collapsedLabel:String="播放实况",collapsedTag:String="play-motion-photo",closeLabel:String="返回照片") {
    val phase by animateFloatAsState(if(expanded)1f else 0f,spring(dampingRatio=.68f,stiffness=380f),label="live-controls-morph")
    val visible=phase.coerceIn(0f,1f)
    val source=remember { MutableInteractionSource() };val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if(pressed)1f else 0f,MaterialTheme.motionScheme.fastSpatialSpec(),label="live-controls-press")
    val labelWidth=with(LocalDensity.current) { rememberTextMeasurer().measure(collapsedLabel,MaterialTheme.typography.labelLarge).size.width.toDp() }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val small=(labelWidth+48.dp).coerceAtMost(maxWidth*.7f)
        val width=if(once)(small+(maxWidth*.96f-small)*phase).coerceIn(small*.94f,maxWidth) else maxWidth*.96f
        val height=if(once)(40.dp+8.dp*phase).coerceIn(38.dp,50.dp) else 48.dp
        Surface(Modifier.width(width).testTag("media-playback-surface").graphicsLayer { scaleX=1f-.025f*press;scaleY=1f-.025f*press },
            shape=RoundedCornerShape(height/2*(1f-.45f*press.coerceIn(0f,1f))),
            color=lerp(MaterialTheme.colorScheme.secondaryContainer,MaterialTheme.colorScheme.surfaceContainerHigh,visible).copy(alpha=.94f)) {
        Row(Modifier.height(height).padding(horizontal=4.dp*visible).clipToBounds(),verticalAlignment=Alignment.CenterVertically) {
            // One play button survives the entire morph; the label yields its width to the track.
            FilledTonalButton(onClick=feedbackClick {
                if(!expanded)start() else { val player=state.player ?: return@feedbackClick
                    if(player.playbackState==androidx.media3.common.Player.STATE_ENDED)player.seekTo(0)
                    if(player.isPlaying)player.pause() else player.play() }
            },modifier=Modifier.width(small+(48.dp-small)*visible).semantics { contentDescription=if(!expanded)collapsedLabel else if(state.playing)"暂停" else "播放" }
                .then(if(!expanded)Modifier.testTag(collapsedTag) else Modifier),
                contentPadding=PaddingValues(4.dp),interactionSource=source,shapes=expressivePillShapes(),
                colors=ButtonDefaults.filledTonalButtonColors(containerColor=Color.Transparent,
                    contentColor=lerp(MaterialTheme.colorScheme.onSecondaryContainer,MaterialTheme.colorScheme.onSurface,visible))) {
                Icon(if(state.playing&&expanded)Icons.Default.Pause else Icons.Default.PlayArrow,null,
                    Modifier.width(24.dp*visible).clipToBounds().graphicsLayer { alpha=visible })
                Box(Modifier.width(labelWidth*(1f-visible)).clipToBounds().graphicsLayer { alpha=1f-visible }) { Text(collapsedLabel,maxLines=1,softWrap=false) }
            }
            if(visible>0f)Row(Modifier.weight(1f).clipToBounds().graphicsLayer {
                alpha=visible;scaleX=.82f+.18f*phase;scaleY=scaleX;transformOrigin=TransformOrigin(0f,.5f)
            },verticalAlignment=Alignment.CenterVertically) {
                WaveSeekBar(state,Modifier.weight(1f),expanded)
                IconButton(feedbackClick { state.muted=!state.muted;state.player?.volume=if(state.muted)0f else 1f },
                    Modifier.width(48.dp*visible),enabled=expanded) {
                    Icon(if(state.muted)Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,"切换声音") }
                if(once)IconButton(feedbackClick(close),Modifier.width(48.dp*visible),enabled=expanded) { Icon(Icons.Default.Close,closeLabel) }
            }
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
