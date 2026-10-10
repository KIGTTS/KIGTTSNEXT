package com.kigtts.next.ui.media

import android.webkit.WebView
import com.kigtts.next.media.WebSafeArea

/** Layouts with identical insets do not need another DOM mutation or JavaScript evaluation. */
internal class WebSafeAreaApplier {
    private var last: Pair<MediaSafeArea, Int>? = null
    fun apply(view: WebView, area: MediaSafeArea, width: Int, force: Boolean = false, complete: () -> Unit = {}) {
        if (width <= 0) return
        val key = area to width
        if (!force && last == key) return
        last = key
        view.evaluateJavascript(WebSafeArea.script(area.top, area.right, area.bottom, area.left, width)) { complete() }
    }
}
