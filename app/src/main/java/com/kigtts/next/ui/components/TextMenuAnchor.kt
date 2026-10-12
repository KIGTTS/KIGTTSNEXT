package com.kigtts.next.ui.components

import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput

/** Field ownership prevents a stale selection from borrowing another field's bounds. */
internal class TextMenuAnchor {
    var coordinates:LayoutCoordinates? by mutableStateOf(null)
    var windowBounds by mutableStateOf(Rect.Zero)
    var focused by mutableStateOf(false)
    var dismiss:()->Unit={}
    var dismissed=false
}

@Composable
internal fun Modifier.textMenuAnchor(dismissSelection:()->Unit):Modifier {
    val anchor=remember { TextMenuAnchor() }
    val dismiss by rememberUpdatedState(dismissSelection)
    SideEffect { anchor.dismiss={dismiss()} }
    DisposableEffect(anchor) { onDispose { anchor.focused=false;anchor.coordinates=null } }
    return onFocusChanged { anchor.focused=it.isFocused;if(!it.isFocused)anchor.dismissed=false }.onGloballyPositioned {
        anchor.coordinates=it;anchor.windowBounds=it.boundsInWindow()
    }
        .pointerInput(anchor) { awaitEachGesture { awaitFirstDown(requireUnconsumed=false);anchor.dismissed=false } }
        .appendTextContextMenuComponents { item(anchor,"") { } }
}
