package com.kigtts.next.ui.cards

import androidx.compose.runtime.*

internal class CardPageChromeState {
    var albums: State<Boolean>? by mutableStateOf(null)
    var albumStage: State<Int>? by mutableStateOf(null)
    var panelEndInset: State<() -> Float>? by mutableStateOf(null)
}

/** Read the panel's live width during toolbar measurement, including while a finger is down. */
@Composable internal fun RegisterCardPanelInset(endInsetPx: () -> Float) {
    val host = LocalCardPageChrome.current ?: return
    val source = rememberUpdatedState(endInsetPx)
    DisposableEffect(host, source) {
        host.panelEndInset = source
        onDispose { if (host.panelEndInset === source) host.panelEndInset = null }
    }
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
