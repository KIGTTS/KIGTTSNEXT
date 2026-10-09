package com.kigtts.next.ui.media

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import com.kigtts.next.media.MediaEntry
import com.kigtts.next.media.MediaKind
import com.kigtts.next.state.*
import com.kigtts.next.ui.navigation.LocalPageActive

/** The outgoing media stays mounted until its fade finishes; it can no longer receive input. */
@Composable
fun MediaTransition(entry:MediaEntry?,state:AppState,model:AppViewModel,modifier:Modifier=Modifier,
    renderFullCardMedia:Boolean=true,empty:@Composable ()->Unit={}) {
    val pageActive=LocalPageActive.current
    AnimatedContent(entry,modifier,contentKey={ it?.id ?: "text" },label="subtitle-media-transition",
        transitionSpec={ (fadeIn(tween(220)) togetherWith (fadeOut(tween(180))+scaleOut(tween(180),targetScale=.98f))).using(null) }) { retained ->
        val active=pageActive&&retained?.id==entry?.id
        val painted=if(retained==null)!renderFullCardMedia else renderFullCardMedia||retained.kind in setOf(MediaKind.WEB,MediaKind.HTML)
        val input=if(active)Modifier else Modifier.clearAndSetSemantics {
            if(painted)testTag="media-exit/"+(retained?.id ?: "text")
        }.pointerInput(Unit) { awaitEachGesture {
            do { val event=awaitPointerEvent(PointerEventPass.Initial);event.changes.forEach { it.consume() } } while(event.changes.any { it.pressed })
        } }
        Box(Modifier.fillMaxSize().then(input)) {
            CompositionLocalProvider(LocalPageActive provides active) {
                if(retained==null)empty() else if(painted)MediaContent(retained,state.copy(mediaId=retained.id),model,Modifier.fillMaxSize()
                    .graphicsLayer { rotationZ=if(state.subtitleFlipped&&!state.mediaEditing)180f else 0f })
            }
        }
    }
}
