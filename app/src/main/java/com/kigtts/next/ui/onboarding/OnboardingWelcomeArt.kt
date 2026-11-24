package com.kigtts.next.ui.onboarding

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.kigtts.next.i18n.uiText

private const val ART_URL = "https://appassets.androidplatform.net/assets/onboarding/welcome.svg"

@Composable
internal fun OnboardingWelcomeArt(
    modifier: Modifier,
    active: Boolean,
    reducedMotion: Boolean,
    tilt: State<Offset>,
) {
    val owner = LocalLifecycleOwner.current
    val description = uiText("onboarding.welcome.art")
    var ready by remember { mutableStateOf<WelcomeArtView?>(null) }
    AndroidView(
        factory = { context ->
            try {
                WelcomeArtView(context) { loaded -> ready = loaded }
            } catch (error: RuntimeException) {
                Log.w("OnboardingWelcome", "Cannot create the welcome illustration", error)
                View(context)
            }
        },
        modifier = modifier.semantics { contentDescription = description },
        onRelease = { (it as? WelcomeArtView)?.dispose() },
    )
    LaunchedEffect(ready, active, reducedMotion, owner, tilt) {
        val view = ready ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            view.setMotion(active = true, reducedMotion)
            try {
                snapshotFlow { tilt.value }.collect { view.setTilt(it) }
            } finally {
                view.setMotion(active = false, reducedMotion)
            }
        }
    }
}

private class WelcomeArtView(context: Context, onReady: (WelcomeArtView) -> Unit) : WebView(context) {
    private var disposed = false

    init {
        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context.applicationContext))
            .build()
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
        }
        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.toString() == ART_URL) return assets.shouldInterceptRequest(request.url)
                return WebResourceResponse("text/plain", "UTF-8", null)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onPageFinished(view: WebView, url: String) {
                if (!disposed && url == ART_URL) onReady(this@WelcomeArtView)
            }
        }
        setBackgroundColor(Color.TRANSPARENT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        loadUrl(ART_URL)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (handled) parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    fun setMotion(active: Boolean, reduced: Boolean) {
        script("window.setWelcomeMotion?.($active,$reduced)")
    }

    fun setTilt(tilt: Offset) {
        script("window.setWelcomeTilt?.(${tilt.x},${tilt.y})")
    }

    private fun script(command: String) {
        if (!disposed) evaluateJavascript(command, null)
    }

    fun dispose() {
        disposed = true
        destroy()
    }
}
