package com.kigtts.next.ui.onboarding

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebViewClientCompat
import com.kigtts.next.i18n.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ART_ORIGIN = "https://kigtts-welcome.invalid/"
internal const val WELCOME_ART_ASPECT = 1576f / 1200f

@Composable
internal fun OnboardingWelcomeArt(
    modifier: Modifier,
    active: Boolean,
    reducedMotion: Boolean,
    tilt: State<Offset>,
) {
    val owner = LocalLifecycleOwner.current
    val description = uiText("onboarding.welcome.art")
    val assets = LocalContext.current.assets
    val document by produceState<String?>(null, assets) {
        value = withContext(Dispatchers.IO) {
            assets.open("onboarding/welcome.svg").bufferedReader(Charsets.UTF_8).use { input ->
                val svg = input.readText().substringAfter("?>")
                """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;overflow:hidden}body>svg{display:block;width:100%;height:auto!important;aspect-ratio:$WELCOME_ART_ASPECT;touch-action:none;opacity:0}</style>
</head><body>$svg</body></html>"""
            }
        }
    }
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
        update = { view -> document?.let { (view as? WelcomeArtView)?.loadArt(it) } },
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
    private var loading = false

    init {
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
        }
        webViewClient = object : WebViewClientCompat() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onPageFinished(view: WebView, url: String) {
                script("document.getElementById('welcome-art') !== null") { hasArt ->
                    if (!disposed && hasArt == "true") onReady(this@WelcomeArtView)
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.w("OnboardingWelcome", "Welcome illustration renderer stopped")
                (parent as? android.view.ViewGroup)?.removeView(this@WelcomeArtView)
                dispose()
                return true
            }
        }
        setBackgroundColor(Color.TRANSPARENT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    fun loadArt(document: String) {
        if (disposed || loading) return
        loading = true
        loadDataWithBaseURL(ART_ORIGIN, document, "text/html", "UTF-8", ART_ORIGIN)
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

    private fun script(command: String, result: android.webkit.ValueCallback<String>? = null) {
        if (!disposed) evaluateJavascript(command, result)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        destroy()
    }
}
