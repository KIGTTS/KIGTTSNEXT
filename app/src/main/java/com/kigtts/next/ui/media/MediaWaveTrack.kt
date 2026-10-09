@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.sin

/** Explicit accumulated phase avoids M3 alpha01's phase reset at zero speed. */
internal class MediaWaveClock {
    var phase by mutableFloatStateOf(0f);private set
    private var previous:Long?=null
    private var previousSpeed=1f
    fun frame(nanos:Long,speed:Float=1f) {
        previous?.let { phase=(phase+(nanos-it).coerceAtLeast(0L)/1_000_000_000f*(previousSpeed+speed)/2f)%1f }
        previous=nanos;previousSpeed=speed
    }
    fun pause() { previous=null }
}

/** MD3E colors/strokes; amplitude and phase velocity ease together into a straight paused track. */
@Composable
internal fun MediaWaveTrack(progress:()->Float,playing:Boolean,modifier:Modifier=Modifier) {
    val clock=remember { MediaWaveClock() };val path=remember { Path() }
    val motion=remember { Animatable(if(playing)1f else 0f) }
    val color=WavyProgressIndicatorDefaults.indicatorColor
    val track=WavyProgressIndicatorDefaults.trackColor
    val stroke=WavyProgressIndicatorDefaults.linearIndicatorStroke
    val wavelength=WavyProgressIndicatorDefaults.LinearDeterminateWavelength
    LaunchedEffect(playing) { motion.animateTo(if(playing)1f else 0f,tween(240,easing=FastOutSlowInEasing)) }
    LaunchedEffect(playing) {
        try { while(isActive&&(playing||motion.value>0f))withFrameNanos { clock.frame(it,motion.value) } }
        finally { if(motion.value==0f)clock.pause() }
    }
    Canvas(modifier.fillMaxWidth().height(WavyProgressIndicatorDefaults.LinearContainerHeight)) {
        val fraction=progress().coerceIn(0f,1f);val mid=size.height/2
        val halfStroke=stroke.width/2;val head=size.width*fraction
        val gap=6.dp.toPx();val end=(head-gap).coerceAtLeast(halfStroke)
        val wave=wavelength.toPx();val amplitude=(mid-halfStroke).coerceAtLeast(0f)*motion.value
        val startTrack=(head+gap).coerceAtMost(size.width-halfStroke)
        if(startTrack<size.width-halfStroke)drawLine(track,Offset(startTrack,mid),Offset(size.width-halfStroke,mid),stroke.width,StrokeCap.Round)
        if(fraction>0f) {
            path.reset();var x=halfStroke
            fun y(at:Float)=mid+amplitude*sin((at/wave+clock.phase)*2f*Math.PI).toFloat()
            path.moveTo(x,y(x))
            while(x<end) { x=(x+wave/16f).coerceAtMost(end);path.lineTo(x,y(x)) }
            drawPath(path,color,style=Stroke(stroke.width,cap=StrokeCap.Round))
        }
    }
}
