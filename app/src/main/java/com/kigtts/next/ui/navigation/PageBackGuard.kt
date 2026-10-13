package com.kigtts.next.ui.navigation

import androidx.compose.runtime.*

/** An editor can guard both the toolbar Back button and system return without owning navigation. */
val LocalPageBackAction = compositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun RegisterPageBackGuard(enabled: Boolean, blocked: () -> Unit) {
    val host = LocalPageToolbarActions.current ?: return
    val handler = rememberUpdatedState<() -> Boolean> { if (enabled) { blocked(); true } else false }
    DisposableEffect(host, handler) {
        host.backGuard = handler
        onDispose { if (host.backGuard === handler) host.backGuard = null }
    }
}
