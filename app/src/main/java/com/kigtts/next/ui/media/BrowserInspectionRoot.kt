package com.kigtts.next.ui.media

import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import com.kigtts.next.media.BrowserInspector

@Composable internal fun RegisterBrowserInspectionRoot() {
    val view = LocalView.current
    DisposableEffect(view) {
        val root = view.rootView as? ViewGroup
        if (root != null) BrowserInspector.register(root)
        onDispose { if (root != null) BrowserInspector.unregister(root) }
    }
}
