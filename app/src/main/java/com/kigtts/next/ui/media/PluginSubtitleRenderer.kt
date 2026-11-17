package com.kigtts.next.ui.media

import android.graphics.Color
import android.net.Uri
import android.os.FileObserver
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.media.WebContentPolicy
import com.kigtts.next.media.applyKigttsTheme
import com.kigtts.next.media.installKigttsThemeAtDocumentStart
import com.kigtts.next.media.WebThemeState
import com.kigtts.next.state.SubtitleRendererSelection
import com.kigtts.next.ui.navigation.LocalPageActive
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Offline display only: no native bridge, network, device grants, downloads or editable document. */
@Composable
fun PluginSubtitleRenderer(selection: SubtitleRendererSelection, text: String, placeholder: String,
    fontSizePercent: Float, modifier: Modifier = Modifier, safeArea: MediaSafeArea = MediaSafeArea(),
    onQuickSwipe: (Boolean) -> Unit = {}, onUnavailable: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pageActive = LocalPageActive.current
    val currentPageActive by rememberUpdatedState(pageActive)
    val currentUnavailable by rememberUpdatedState(onUnavailable)
    var rendererSettings by remember(selection) { mutableStateOf("{}") }
    val scope = rememberCoroutineScope()
    // Watch atomic replacements too; style changes update the existing page and retain its text.
    DisposableEffect(selection) {
        val watcherScope = kotlinx.coroutines.CoroutineScope(scope.coroutineContext + kotlinx.coroutines.SupervisorJob(scope.coroutineContext[kotlinx.coroutines.Job]))
        fun reload() = watcherScope.launch {
            rendererSettings = withContext(Dispatchers.IO) { SubtitleRendererSettings.read(selection.root) }
        }
        @Suppress("DEPRECATION")
        val observer = object : FileObserver(selection.root.path, CLOSE_WRITE or MOVED_TO or DELETE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == SubtitleRendererSettings.FILE) reload()
            }
        }
        observer.startWatching()
        reload()
        onDispose { observer.stopWatching(); watcherScope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }
    val density = LocalDensity.current.density
    val latestUpdate by rememberUpdatedState(SubtitleRendererProtocol.update(text, placeholder, fontSizePercent, rendererSettings, safeArea, density))
    val theme by WebThemeState.themes.collectAsState()
    var ready by remember(selection) { mutableStateOf(false) }
    var disposed by remember(selection) { mutableStateOf(false) }
    var failed by remember(selection) { mutableStateOf(false) }
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val windowSettings = remember(rendererSettings) { RendererWindowSettings.parse(rendererSettings) }
    val visible = pageActive && lifecycleState.isAtLeast(Lifecycle.State.STARTED) && ready && !failed
    SubtitleRendererWindow(windowSettings, visible)
    val quickSwipeCallback by rememberUpdatedState(onQuickSwipe)
    DisposableEffect(visible, windowSettings.quickSwipe) {
        quickSwipeCallback(visible && windowSettings.quickSwipe)
        onDispose { quickSwipeCallback(false) }
    }
    fun active() = currentPageActive && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    fun unavailable() { if (!disposed && !failed) { failed = true; currentUnavailable() } }
    val origin = remember(selection.owner) {
        val digest = MessageDigest.getInstance("SHA-256").digest(selection.owner.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
        "https://renderer-$digest.kigtts.invalid"
    }
    fun permitted(url: String) = runCatching {
        val address = Uri.parse(url)
        address.scheme == "https" && address.authority == Uri.parse(origin).authority && address.path.orEmpty().startsWith("/content/")
    }.getOrDefault(false)
    val web = remember(selection) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.TRANSPARENT)
            isLongClickable = false
            setOnLongClickListener { true }
            isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
            settings.apply {
                disabledActionModeMenuItems = WebSettings.MENU_ITEM_PROCESS_TEXT or WebSettings.MENU_ITEM_WEB_SEARCH or WebSettings.MENU_ITEM_SHARE
                javaScriptEnabled = true; allowFileAccess = false; allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE; domStorageEnabled = false; databaseEnabled = false
                mediaPlaybackRequiresUserGesture = true; javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false); setSupportZoom(false); builtInZoomControls = false
                displayZoomControls = false; textZoom = 100
                useWideViewPort = false; loadWithOverviewMode = false
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) = request.deny()
                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) = callback.invoke(origin, false, false)
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    val file = if (request.method == "GET" && permitted(request.url.toString()))
                        WebContentPolicy.file(selection.root, request.url.path.orEmpty().removePrefix("/content/")) else null
                    if (file == null) return WebResourceResponse("text/plain", "UTF-8", 403, "Unavailable", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    val mime = when (file.extension.lowercase()) {
                        "js" -> "application/javascript"; "css" -> "text/css"; "html", "htm" -> "text/html"
                        "svg" -> "image/svg+xml"; else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
                    }
                    val policy = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'none'; frame-src 'none'; worker-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
                    return runCatching { WebResourceResponse(mime, "UTF-8", 200, "OK", mapOf("Content-Security-Policy" to policy,
                        "Permissions-Policy" to "camera=(), microphone=(), geolocation=(), accelerometer=(), gyroscope=(), magnetometer=()"), file.inputStream()) }
                        .getOrElse { WebResourceResponse("text/plain", "UTF-8", 403, "Unavailable", emptyMap(), ByteArrayInputStream(byteArrayOf())) }
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (!permitted(url) || disposed || failed) return
                    view.applyKigttsTheme()
                    var attempts = 0
                    fun handshake() {
                        if (disposed || failed) return
                        view.evaluateJavascript(latestUpdate) { result ->
                            if (disposed || failed) return@evaluateJavascript
                            if (result == "true") {
                                ready = true
                                view.evaluateJavascript(SubtitleRendererProtocol.activity(active()), null)
                            } else if (++attempts < 50) view.postDelayed({ handshake() }, 100) else unavailable()
                        }
                    }
                    handshake()
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) unavailable()
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    if (request.isForMainFrame) unavailable()
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    unavailable(); return true
                }
            }
            installKigttsThemeAtDocumentStart()
            loadUrl(origin + "/content/" + Uri.encode(selection.mainPage, "/"))
        }
    }
    LaunchedEffect(latestUpdate, ready) { if (ready && !disposed) web.evaluateJavascript(latestUpdate, null) }
    LaunchedEffect(theme, ready) { if (ready && !disposed) web.applyKigttsTheme() }
    LaunchedEffect(pageActive, ready) {
        if (pageActive) web.onResume() else web.onPause()
        if (ready && !disposed) web.evaluateJavascript(SubtitleRendererProtocol.activity(active()), null)
    }
    DisposableEffect(web, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                web.evaluateJavascript(SubtitleRendererProtocol.activity(false), null); web.onPause()
            } else if (event == Lifecycle.Event.ON_START && currentPageActive) {
                web.onResume(); if (ready) web.evaluateJavascript(SubtitleRendererProtocol.activity(true), null)
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            disposed = true; lifecycle.removeObserver(observer)
            web.stopLoading(); web.removeAllViews(); web.destroy()
        }
    }
    AndroidView(factory = { web }, modifier = modifier.testTag("plugin-subtitle-renderer").semantics {
        stateDescription = text.ifEmpty { placeholder }
    })
}
