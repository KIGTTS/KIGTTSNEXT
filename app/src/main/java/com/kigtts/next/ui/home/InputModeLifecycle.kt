package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager

/** Start restoring the home layout when IME dismissal starts, while its insets still animate. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObserveImeDismissal(editing: Boolean, suspended: Boolean = false, finish: () -> Unit) {
    val visible = WindowInsets.isImeVisible
    val density = LocalDensity.current
    val bottom = WindowInsets.ime.getBottom(density)
    val target = WindowInsets.imeAnimationTarget.getBottom(density)
    val focus = LocalFocusManager.current
    val latestFinish by rememberUpdatedState(finish)
    val session = remember { ImeInputSession() }
    LaunchedEffect(editing, visible, bottom, target, suspended) {
        if (session.update(editing, suspended, visible, bottom, target)) {
            focus.clearFocus()
            latestFinish()
        }
    }
}

internal class ImeInputSession {
    private var shown = false
    private var finished = false
    fun update(editing: Boolean, suspended: Boolean, visible: Boolean, bottom: Int, target: Int): Boolean {
        if (!editing || suspended) { shown = false; finished = false; return false }
        if (finished) return false
        if (!shown) { shown = visible; return false }
        // Floating keyboards can be visible with zero insets; do not dismiss them on opening.
        if (!visible || bottom > 0 && target == 0) { finished = true; return true }
        return false
    }
}
