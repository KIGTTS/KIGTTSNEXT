package com.kigtts.next.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) session.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.pause() }
    }
    LaunchedEffect(active) { if (!active) session.pause() }
    LaunchedEffect(session, active) {
        if (active) snapshotFlow { playback.playing }.collectLatest { playing ->
            if (playing) while (true) withFrameNanos { if (!playback.scrubbing) playback.position = playback.player?.currentPosition?.toFloat() ?: 0f }
        }
    }
    Column(modifier.testTag("audio-card-controls"), horizontalAlignment = Alignment.CenterHorizontally) {
        session.error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
        PlaybackControls(playback, session.expanded, true, session::restart, session::collapse,
            Modifier.fillMaxWidth().padding(horizontal = 8.dp), label, playTag, "收起音频控件")
    }
}
