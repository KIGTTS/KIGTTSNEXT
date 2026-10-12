package com.kigtts.next.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.audio.AudioCardSession
import com.kigtts.next.ui.navigation.LocalPageActive
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AudioCardControls(session: AudioCardSession, modifier: Modifier = Modifier, label: String = "播放音频", playTag: String = "play-card-audio") {
    val active = LocalPageActive.current; val lifecycle = LocalLifecycleOwner.current.lifecycle
    val playback = session.playback
    val visible by rememberUpdatedState(active)
    SideEffect { playback.allowed=active&&lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            playback.allowed=visible&&lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (event == Lifecycle.Event.ON_STOP) { playback.cancelScrub();session.pause() }
        }
        lifecycle.addObserver(observer)
        onDispose { playback.allowed=false;playback.cancelScrub();lifecycle.removeObserver(observer); session.pause() }
    }
    LaunchedEffect(active) { if (!active) { playback.cancelScrub();session.pause() } }
    LaunchedEffect(session, active) {
        if (active) snapshotFlow { playback.playing }.collectLatest { playing ->
            if (playing) while (true) withFrameNanos { if (!playback.scrubbing) playback.position = playback.player?.currentPosition?.toFloat() ?: 0f }
        }
    }
    Column(modifier.testTag("audio-card-controls"), horizontalAlignment = Alignment.CenterHorizontally) {
        session.error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
        PlaybackControls(playback, session.expanded, true, session::restart, session::collapse,
            Modifier.fillMaxWidth().padding(horizontal = 8.dp), label, playTag, "收起音频控件",collapsedIcon=Icons.Default.AudioFile)
    }
}
