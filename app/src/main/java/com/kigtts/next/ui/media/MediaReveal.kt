package com.kigtts.next.ui.media

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun Modifier.mediaReveal(ready:Boolean):Modifier {
    val reveal=remember { Animatable(0f) }
    LaunchedEffect(ready) { if(ready)reveal.animateTo(1f,tween(240,easing=FastOutSlowInEasing)) }
    return graphicsLayer { alpha=reveal.value;scaleX=.98f+.02f*reveal.value;scaleY=scaleX }
}
