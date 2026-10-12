package com.kigtts.next.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/** Focus starts collapsing before the IME changes the viewport. Multiple fields share one owner. */
internal class PageInputState {
    val sources = mutableStateMapOf<Any, State<Boolean>>()
    val focused by derivedStateOf { sources.values.any { it.value } }
}

@Composable
internal fun RegisterPageInput(focused: Boolean) {
    val host = LocalPageToolbarActions.current ?: return
    val key = remember { Any() }
    val current = rememberUpdatedState(focused)
    DisposableEffect(host, key) {
        host.input.sources[key] = current
        onDispose { host.input.sources.remove(key) }
    }
}

@Composable
internal fun Modifier.pageInputFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    RegisterPageInput(focused)
    return onFocusChanged { focused = it.hasFocus }
}
