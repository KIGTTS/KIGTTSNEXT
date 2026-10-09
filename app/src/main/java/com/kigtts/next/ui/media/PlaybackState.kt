package com.kigtts.next.ui.media

import androidx.compose.runtime.*
import androidx.media3.common.Player

@Stable
class PlaybackState {
    var player:Player? by mutableStateOf(null)
    var playing by mutableStateOf(false)
    var rendered by mutableStateOf(false)
    var position by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(1f)
    var scrubbing by mutableStateOf(false)
    var muted by mutableStateOf(false)
}
