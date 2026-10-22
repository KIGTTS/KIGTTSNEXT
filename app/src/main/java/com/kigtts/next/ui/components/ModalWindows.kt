package com.kigtts.next.ui.components

import androidx.compose.runtime.*

/** Local pickers must suspend IME-dismiss handling just like plugin-owned dialogs do. */
internal class ModalWindows {
    var count by mutableIntStateOf(0)
        private set
    fun enter() { count++ }
    fun leave() { count = (count - 1).coerceAtLeast(0) }
}
internal val LocalModalWindows = staticCompositionLocalOf<ModalWindows?> { null }
