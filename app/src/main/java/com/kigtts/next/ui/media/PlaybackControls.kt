@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.media

import com.kigtts.next.ui.components.animateEnabled
import com.kigtts.next.ui.components.enabledContentMotion

import com.kigtts.next.ui.components.MotionIconButton

import androidx.compose.animation.core.*
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.delay
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.expressivePillShapes
import com.kigtts.next.ui.components.rememberImmediateButtonPress
import com.kigtts.next.ui.components.immediateButtonPress

/** The same pill expands into controls; only the seek bar reads per-frame progress. */
@Composable
fun PlaybackControls(state:PlaybackState,expanded:Boolean,once:Boolean,start:()->Unit,close:()->Unit,modifier:Modifier=Modifier,
    collapsedLabel:String="播放实况",collapsedTag:String="play-motion-photo",closeLabel:String="返回照片",idleCollapse:Boolean=true,
    collapsedIcon:ImageVector=Icons.Default.MotionPhotosOn) {
    var idle by remember { mutableStateOf(false) }
    val compact by animateFloatAsState(if(idle&&state.playing&&!state.scrubbing)1f else 0f,
        tween(240,easing=FastOutSlowInEasing),label="playback-idle-morph")
    val phase by animateFloatAsState(if(expanded)1f else 0f,spring(dampingRatio=.68f,stiffness=380f),label="live-controls-morph")
    val visible=phase.coerceIn(0f,1f)
    val source=remember { MutableInteractionSource() };val pressed by source.collectIsPressedAsState()
    LaunchedEffect(idleCollapse,expanded,state.playing,state.scrubbing,state.interaction,pressed) {
        idle=false
        if(idleCollapse&&expanded&&state.playing&&!state.scrubbing&&!pressed) { delay(3500);idle=true }
    }
    val immediate=rememberImmediateButtonPress(source)
    val active=com.kigtts.next.ui.navigation.LocalPageActive.current
    val enabled=active&&compact<.96f&&(!expanded||state.allowed&&state.player!=null)
    LaunchedEffect(enabled) { if(!enabled)immediate.cancel() }
    val press by animateFloatAsState(if(pressed)1f else 0f,MaterialTheme.motionScheme.fastSpatialSpec(),label="live-controls-press")
    val labelWidth=with(LocalDensity.current) { rememberTextMeasurer().measure(collapsedLabel,MaterialTheme.typography.labelLarge).size.width.toDp() }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val small=(labelWidth+48.dp).coerceAtMost(maxWidth*.7f)
        val normalWidth=if(once)(small+(maxWidth*.96f-small)*phase).coerceIn(small*.94f,maxWidth) else maxWidth*.96f
        val width=normalWidth*(1f-.18f*compact)
        val height=if(once)(40.dp+8.dp*phase).coerceIn(38.dp,50.dp) else 48.dp
        Surface(Modifier.width(width).testTag("media-playback-surface").graphicsLayer {
            scaleX=1f-.025f*press;scaleY=1f-.025f*press;translationY=20.dp.toPx()*compact
        },
            shape=RoundedCornerShape(height/2*(1f-.45f*press.coerceIn(0f,1f))),
            color=lerp(MaterialTheme.colorScheme.secondaryContainer,MaterialTheme.colorScheme.surfaceContainerHigh,visible).copy(alpha=.94f*(1f-compact))) {
        Row(Modifier.height(height).padding(horizontal=4.dp*visible).clipToBounds(),verticalAlignment=Alignment.CenterVertically) {
            // One play button survives the entire morph; the label yields its width to the track.
            FilledTonalButton(onClick=feedbackClick {
                immediate.click()
                state.wake();if(!expanded)start() else state.toggle()
            },enabled=enabled,modifier=Modifier.then(if(enabled)Modifier.immediateButtonPress(immediate)else Modifier).width((small+(48.dp-small)*visible)*(1f-compact)).graphicsLayer { alpha=1f-compact }.semantics { contentDescription=if(!expanded)collapsedLabel else if(state.playing)"暂停" else "播放" }
                .then(if(!expanded)Modifier.testTag(collapsedTag) else Modifier),
                contentPadding=PaddingValues(4.dp),interactionSource=immediate.native,shapes=expressivePillShapes(),
                colors=ButtonDefaults.filledTonalButtonColors(containerColor=Color.Transparent,
                    contentColor=lerp(MaterialTheme.colorScheme.onSecondaryContainer,MaterialTheme.colorScheme.onSurface,visible)).animateEnabled(enabled)) {
                Crossfade(if(!expanded)collapsedIcon else if(state.playing)Icons.Default.Pause else Icons.Default.PlayArrow,
                    Modifier.size(24.dp).testTag("media-playback-icon"),animationSpec=tween(120),label="playback-icon") { icon->Icon(icon,null,Modifier.size(24.dp)) }
                Spacer(Modifier.width(8.dp*(1f-visible)))
                Box(Modifier.width(labelWidth*(1f-visible)).clipToBounds().graphicsLayer { alpha=1f-visible }) { Text(collapsedLabel,maxLines=1,softWrap=false) }
            }
            if(visible>0f)Row(Modifier.weight(1f).clipToBounds().graphicsLayer {
                alpha=visible;scaleX=.82f+.18f*phase;scaleY=scaleX;transformOrigin=TransformOrigin(0f,.5f)
            },verticalAlignment=Alignment.CenterVertically) {
                WaveSeekBar(state,Modifier.weight(1f),expanded,compact)
                MotionIconButton(feedbackClick { state.wake();state.muted=!state.muted;state.player?.volume=if(state.muted)0f else 1f },
                    Modifier.width(48.dp*visible*(1f-compact)).graphicsLayer { alpha=1f-compact },enabled=enabled&&expanded) {
                    Icon(if(state.muted)Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,"切换声音") }
                if(once)MotionIconButton(feedbackClick { state.cancelScrub();state.wake();close() },Modifier.width(48.dp*visible*(1f-compact)).graphicsLayer { alpha=1f-compact },enabled=active&&expanded&&compact<.96f) { Icon(Icons.Default.Close,closeLabel) }
            }
        }
    }
}
}

@Composable
private fun WaveSeekBar(state:PlaybackState,modifier:Modifier,expanded:Boolean,compact:Float) {
    val duration=state.duration.coerceAtLeast(1f)
    val enabled=expanded&&com.kigtts.next.ui.navigation.LocalPageActive.current&&state.allowed&&state.player!=null&&state.duration>1f
    val source=remember { MutableInteractionSource() }
    DisposableEffect(state,enabled) { if(!enabled)state.cancelScrub();onDispose { state.cancelScrub() } }
    Slider(state.position.coerceIn(0f,duration),state::seek,modifier.testTag("media-wave-seek")
        .then(if(enabled)Modifier.pointerInput(state) { awaitEachGesture {
            awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);state.wake()
            try { do { val event=awaitPointerEvent(PointerEventPass.Final) } while(event.changes.any { it.pressed }) }
            finally { state.finishScrub() }
        } } else Modifier),
        valueRange=0f..duration,enabled=enabled,colors=SliderDefaults.colors().animateEnabled(enabled),
        interactionSource=source,onValueChangeFinished=state::finishScrub,
        // Keep a stable touch/measurement slot. The compact line has no visible handle.
        thumb={ Box(Modifier.size(4.dp,24.dp),contentAlignment=Alignment.Center) {
            SliderDefaults.Thumb(source,enabled=enabled,modifier=Modifier.testTag("media-seek-thumb").graphicsLayer { alpha=1f-compact },
                thumbSize=DpSize(4.dp,24.dp))
        } },
        track={ slider -> MediaWaveTrack(progress={ (slider.value/duration).coerceIn(0f,1f) },playing=state.playing,
            modifier=Modifier.testTag("media-seek-track").enabledContentMotion(enabled),compact=compact) })
}
