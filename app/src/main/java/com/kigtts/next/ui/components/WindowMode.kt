package com.kigtts.next.ui.components

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.MultiWindowModeChangedInfo
import androidx.core.util.Consumer

internal val LocalMultiWindowMode = compositionLocalOf<Boolean?> { null }

/** Observe window-mode changes without confusing a compact fullscreen phone with a small window. */
@Composable
internal fun rememberMultiWindowMode(): Boolean {
    LocalMultiWindowMode.current?.let { return it }
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext?.takeUnless { base -> base === it } }
            .filterIsInstance<ComponentActivity>().firstOrNull()
    }
    var multiWindow by remember(activity) { mutableStateOf(activity?.isInMultiWindowMode == true) }
    DisposableEffect(activity) {
        val observer = Consumer<MultiWindowModeChangedInfo> { multiWindow = it.isInMultiWindowMode }
        activity?.addOnMultiWindowModeChangedListener(observer)
        onDispose { activity?.removeOnMultiWindowModeChangedListener(observer) }
    }
    return multiWindow
}
