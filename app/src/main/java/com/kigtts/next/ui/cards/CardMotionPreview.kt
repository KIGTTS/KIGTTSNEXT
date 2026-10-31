@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.kigtts.next.ui.cards

import android.graphics.Bitmap
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.kigtts.next.cards.*
import com.kigtts.next.ui.media.PlaybackControls
import com.kigtts.next.ui.media.PlaybackState
import com.kigtts.next.ui.media.LocalVideoPlayerFactory
import com.kigtts.next.ui.media.videoFrameLayout
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.media.MediaScale
import androidx.compose.ui.unit.IntSize
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.*

@Stable internal class CardMotionPreview(val clip: CardMotionClip, val overlay: Bitmap, val effect: CardMotionPaperEffect) {
    val playback = PlaybackState().apply { muted = true }
    var opened by mutableStateOf(false)
    var error by mutableStateOf(false)
}

/** Only a preview requests this metadata/overlay; gallery and flip-page thumbnails remain ordinary stills. */
@Composable internal fun rememberCardMotionPreview(stickers: StickerRepository, card: PolaroidCard): CardMotionPreview? {
    val frame = stickers.frameStyle(card)
    val loaded by produceState<CardMotionPreview?>(null, stickers, card, frame) {
        value = withContext(Dispatchers.IO) {
            val source = CardMotionRepository(stickers).get(card.imageId)?.withPhotoStyle(card.photoStyle, stickers::imageFile) ?: return@withContext null
            val overlay = PolaroidRenderer.renderBitmap(stickers, card, transparentPhoto = true, maximumHeight = 720)
            CardMotionPreview(source, overlay, CardMotionPaperEffect(card, frame, overlay, source.colorMatrix, true, source.lut, source.lutStrength, source.effects))
        }
    }
    // The bounded single overlay has ordinary Bitmap lifetime. Do not recycle it ahead of an
    // asynchronously cancelling native GL producer during rotation or native-window disposal.
    return loaded
}

@Composable internal fun CardMotionControls(preview: CardMotionPreview, enabled: Boolean, modifier: Modifier = Modifier) {
    preview.playback.allowed = enabled
    CompositionLocalProvider(LocalPageActive provides (LocalPageActive.current && enabled)) {
    PlaybackControls(preview.playback, preview.opened, true,
        { if (enabled) { preview.error = false; preview.opened = true } }, { preview.opened = false }, modifier.testTag("card-motion-controls"),
        idleCollapse = false)
    }
}

@Composable internal fun CardMotionVideo(preview: CardMotionPreview, enabled: Boolean, modifier: Modifier = Modifier) {
    if (!preview.opened) return
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = preview.playback
    val factory = LocalVideoPlayerFactory.current
    val player = remember(preview) { factory(context).apply {
        setVideoEffects(listOf(preview.effect)); setSeekParameters(SeekParameters.EXACT); volume = if (state.muted) 0f else 1f
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build(), true); setHandleAudioBecomingNoisy(true)
    } }
    val active = rememberUpdatedState(enabled)
    DisposableEffect(player, lifecycle) {
        state.player = player; state.rendered = false; state.duration = preview.clip.durationMs.toFloat()
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { state.rendered = true }
            override fun onIsPlayingChanged(isPlaying: Boolean) { state.playing = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player.duration > 0) state.duration = player.duration.toFloat()
                if (playbackState == Player.STATE_ENDED && !state.scrubbing) preview.opened = false
            }
            override fun onPlayerError(error: PlaybackException) { preview.error = true; preview.opened = false }
        }
        val observer = LifecycleEventObserver { _, _ -> if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) player.pause() }
        lifecycle.addObserver(observer); player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(preview.clip.video))); player.prepare(); if (active.value) player.play()
        onDispose {
            state.cancelScrub(); lifecycle.removeObserver(observer); player.removeListener(listener); player.release()
            if (state.player === player) state.player = null
            state.playing = false; state.rendered = false; state.position = 0f
        }
    }
    LaunchedEffect(enabled) { if (!enabled) player.pause() }
    LaunchedEffect(player) {
        while (true) { withFrameNanos { if (!state.scrubbing) state.position = player.currentPosition.toFloat() } }
    }
    AndroidView(factory = { TextureView(it).apply { isOpaque = false; player.setVideoTextureView(this) } },
        modifier = modifier.fillMaxSize().graphicsLayer { alpha = if (state.rendered) 1f else 0f }
            .videoFrameLayout(IntSize(preview.overlay.width.coerceAtLeast(2) / 2 * 2,
                preview.overlay.height.coerceAtLeast(2) / 2 * 2), MediaScale.FIT).testTag("card-motion-paper"),
        onRelease = { if (state.player === player) player.clearVideoTextureView(it) })
}
