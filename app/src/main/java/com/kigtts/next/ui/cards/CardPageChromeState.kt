package com.kigtts.next.ui.cards

import androidx.compose.runtime.*

internal class CardPageChromeState {
    var albums: State<Boolean>? by mutableStateOf(null)
    var albumStage: State<Int>? by mutableStateOf(null)
}
internal val LocalCardPageChrome = compositionLocalOf<CardPageChromeState?> { null }
internal val LocalCardWindowLandscape = compositionLocalOf<Boolean?> { null }

/** Album state controls the native title without replacing the scene or its browser composition. */
@Composable internal fun RegisterCardAlbumExpanded(expanded: Boolean, stage: Int = if (expanded) 2 else 0) {
    val host = LocalCardPageChrome.current ?: return
    val source = rememberUpdatedState(expanded)
    val position = rememberUpdatedState(stage)
    DisposableEffect(host, source) {
        host.albums = source
        host.albumStage = position
        onDispose { if (host.albums === source) { host.albums = null; host.albumStage = null } }
    }
}
