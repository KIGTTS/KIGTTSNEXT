package com.kigtts.next.ui.media

import android.webkit.WebView
import com.kigtts.next.media.WebSafeArea
import androidx.compose.ui.unit.IntSize

/** Layouts with identical insets do not need another DOM mutation or JavaScript evaluation. */
internal class WebSafeAreaApplier {
    private var last: Pair<MediaSafeArea, IntSize>? = null
    fun apply(view: WebView, area: MediaSafeArea, viewport: IntSize, force: Boolean = false, complete: () -> Unit = {}) {
        if (viewport.width <= 0 || viewport.height <= 0) return
        val key = area to viewport
        if (!force && last == key) return
        last = key
        view.evaluateJavascript(WebSafeArea.script(area.top, area.right, area.bottom, area.left, viewport.width, viewport.height)) { complete() }
    }
}
