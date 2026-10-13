package com.kigtts.next.ui.cards

import androidx.compose.runtime.*

internal class CardPageChromeState {
    var albums: State<Boolean>? by mutableStateOf(null)
}
internal val LocalCardPageChrome = compositionLocalOf<CardPageChromeState?> { null }
internal val LocalCardWindowLandscape = compositionLocalOf<Boolean?> { null }

/** Album state controls the native title without replacing the scene or its browser composition. */
@Composable internal fun RegisterCardAlbumExpanded(expanded: Boolean) {
    val host = LocalCardPageChrome.current ?: return
    val source = rememberUpdatedState(expanded)
    DisposableEffect(host, source) {
        host.albums = source
        onDispose { if (host.albums === source) host.albums = null }
    }
}
