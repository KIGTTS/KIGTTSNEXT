package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager

/** Wait for a shown keyboard before treating its disappearance as the end of input. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObserveImeDismissal(editing: Boolean, finish: () -> Unit) {
    val visible = WindowInsets.isImeVisible
    val focus = LocalFocusManager.current
    val latestFinish by rememberUpdatedState(finish)
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(editing, visible) {
        if (!editing) keyboardWasVisible = false
        else if (visible) keyboardWasVisible = true
        else if (keyboardWasVisible) {
            keyboardWasVisible = false
            focus.clearFocus()
            latestFinish()
        }
    }
}
