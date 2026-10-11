package com.kigtts.next.ui.media

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.navigation.LocalPageActive

@Composable
fun MediaContent(entry: MediaEntry, state: AppState, model: AppViewModel, modifier: Modifier = Modifier) {
    key(entry.id,state.contentRevision) {
        var source by remember { mutableStateOf(model.mediaRepository.file(entry) to model.mediaRepository.file(entry,true)) }
        if(LocalPageActive.current) {
            val current=model.mediaRepository.file(entry) to model.mediaRepository.file(entry,true)
            SideEffect { source=current }
        }
        when (entry.kind) {
            MediaKind.IMAGE, MediaKind.MOTION_PHOTO -> {
                var zoom by remember { mutableFloatStateOf(1f) }; var pan by remember { mutableStateOf(Offset.Zero) }
                var bounds by remember { mutableStateOf(IntSize.Zero) }
                LaunchedEffect(entry.scale) { zoom = 1f; pan = Offset.Zero }
                val live = entry.video != null && (!entry.appleLive || state.appleLivePhotos)
                Box(modifier.testTag("media-image").onSizeChanged { bounds = it }, contentAlignment = Alignment.Center) {
                    MediaImage(source.first, entry.name, Modifier.fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = 1f; pan = Offset.Zero }, onLongPress = { model.enterSubtitleFullscreen() }) }
                        .transformable(rememberTransformableState { scale, offset, _ ->
                            zoom = (zoom * scale).coerceIn(1f, 5f)
                            val limitX = (zoom - 1f) * bounds.width / 2; val limitY = (zoom - 1f) * bounds.height / 2
                            pan = if (zoom == 1f) Offset.Zero else (pan + offset).let { Offset(it.x.coerceIn(-limitX, limitX), it.y.coerceIn(-limitY, limitY)) }
                        }), scale = entry.scale, backdrop = true,
                        contentModifier = Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y })
                    if (live) LivePhotoPlayback(source.second,entry.name,entry.scale,autoPlay=state.contentAutoPlay,
                        playAudio=(state.contentAudioMode ?: MediaAudioMode.defaultFor(entry.kind)).audible(state.autoPlayback),onLongPress=model::enterSubtitleFullscreen)
                }
            }
            MediaKind.VIDEO -> MediaVideo(source.first, entry.name, false, {}, modifier, entry.scale,autoPlay=state.contentAutoPlay,
                playAudio=(state.contentAudioMode ?: MediaAudioMode.defaultFor(entry.kind)).audible(state.autoPlayback),onLongPress=model::enterSubtitleFullscreen)
            MediaKind.HTML, MediaKind.WEB -> MediaWeb(entry, state, model, modifier)
            MediaKind.AUDIO -> Box(modifier, contentAlignment = Alignment.Center) { Text(entry.name) }
        }
    }
}
