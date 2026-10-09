@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.ui.media

import android.view.LayoutInflater
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.kigtts.next.media.MediaScale
import com.kigtts.next.R
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.navigation.LocalPageActive
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun MediaVideo(file: File?, description: String, once: Boolean, ended: () -> Unit, modifier: Modifier = Modifier,
    scale: MediaScale = MediaScale.FIT) {
    if (file == null) { Box(modifier) { Text("视频资源不可用") }; return }
    val context = LocalContext.current; val active = LocalPageActive.current; val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onEnded by rememberUpdatedState(ended)
    val player = remember(file.path) { ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file))); volume = if (once) 0f else 1f
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        prepare(); playWhenReady = once
    } }
    var playing by remember { mutableStateOf(player.isPlaying) }; var muted by remember { mutableStateOf(once) }
    var position by remember { mutableFloatStateOf(0f) }; var duration by remember { mutableFloatStateOf(1f) }; var error by remember { mutableStateOf<String?>(null) }
    var view by remember { mutableStateOf<PlayerView?>(null) }
    var rendered by remember { mutableStateOf(false) }
    val background = rememberVideoBackdrop(file, view, player, active, scale, rendered)
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED && once) onEnded() }
            override fun onPlayerError(exception: PlaybackException) { error = "无法播放此视频：" + exception.errorCodeName }
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer); player.addListener(listener)
        onDispose { lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    LaunchedEffect(active) { if (!active) { player.pause(); if (once) onEnded() } }
    LaunchedEffect(player) { while (true) { position = player.currentPosition.toFloat(); duration = player.duration.takeIf { it > 0 }?.toFloat() ?: 1f; delay(250) } }
    Box(modifier, contentAlignment = Alignment.Center) {
            if (scale == MediaScale.FIT) background?.let { BlurredBackdrop(it, Modifier.fillMaxSize()) }
            AndroidView(factory = { (LayoutInflater.from(it).inflate(R.layout.media_player, android.widget.FrameLayout(it), false) as PlayerView).also { view = it } },
                update = { it.player = player; it.contentDescription = description
                    it.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    it.resizeMode = if (scale == MediaScale.FIT) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM },
                modifier = Modifier.fillMaxSize(), onRelease = { it.player = null; view = null })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 52.dp),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .9f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(feedbackClick { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); if (playing) player.pause() else player.play() }) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "暂停" else "播放")
            }
            Slider(position.coerceIn(0f, duration), { player.seekTo(it.toLong()); position = it }, Modifier.weight(1f), valueRange = 0f..duration)
            IconButton(feedbackClick { muted = !muted; player.volume = if (muted) 0f else 1f }) { Icon(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "切换视频声音") }
            if (once) TextButton(feedbackClick(onEnded)) { Text("照片") }
        }
        }
    }
}
