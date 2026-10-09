package com.kigtts.next.ui.media

import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.navigation.LocalPageActive
import java.io.File

@Composable
fun MediaWeb(entry: MediaEntry, state: AppState, model: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current; val active = LocalPageActive.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    val currentActive by rememberUpdatedState(active)
    fun ownsPage()=currentActive&&model.state.value.mediaId==entry.id
    var ready by remember(entry.id) { mutableStateOf(false) }
    val web = remember(entry.id) {
        WebView(context).apply {
            CookieManager.getInstance().setAcceptCookie(false)
            settings.javaScriptEnabled = true; settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode = WebSettings.LOAD_NO_CACHE; settings.domStorageEnabled = false
            settings.mediaPlaybackRequiresUserGesture = true
            val source = model.mediaRepository.file(entry)
            val cache = if (entry.pluginId == null) File(model.mediaRepository.root, "web-cache/" + entry.id)
                else model.pluginManager.paths.storage(entry.pluginId).file("cache/web/" + entry.id)
            val loader = WebResourceLoader(cache, source?.parentFile, entry.network)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = loader.load(request.url.toString(), request.method)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !(request.url.toString().startsWith(WebContentPolicy.ORIGIN + "/content/") || entry.network && WebContentPolicy.https(request.url.toString()))
                override fun onPageFinished(view: WebView, url: String) { ready=true;if(ownsPage())model.webHistory(view.canGoBack()) }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) { ready=true;if(ownsPage())model.message("网页加载失败：" + error.description) }
                }
            }
            if (entry.kind == MediaKind.HTML && entry.pluginId != null && entry.actions.isNotEmpty()) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(this, "Kigtts", setOf(WebContentPolicy.ORIGIN)) { _, message, origin, mainFrame, _ ->
                        if(ownsPage())WebContentPolicy.action(entry, message.data.orEmpty(), origin.toString(), mainFrame)?.let { (action, inputs) -> model.invoke(entry.pluginId, action, inputs) }
                    }
                } else model.message("当前系统 WebView 不支持插件网页按钮，请更新 Android System WebView")
            }
            loadUrl(if (entry.kind == MediaKind.WEB) entry.primary else WebContentPolicy.ORIGIN + "/content/" + android.net.Uri.encode(source?.name.orEmpty()))
        }
    }
    var lastBack by remember { mutableIntStateOf(state.webBackRevision) }
    LaunchedEffect(state.webBackRevision) { if (ownsPage()&&lastBack != state.webBackRevision) { lastBack = state.webBackRevision; if (web.canGoBack()) web.goBack(); model.webHistory(web.canGoBack()) } }
    LaunchedEffect(ime, active) { if(ownsPage())model.mediaEditing(ime > 0 && web.hasFocus()) }
    LaunchedEffect(active) { if (active) web.onResume() else web.onPause() }
    DisposableEffect(web, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) web.onPause() else if (event == Lifecycle.Event.ON_START && currentActive) web.onResume() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); web.stopLoading(); web.destroy(); if(ownsPage()) { model.webHistory(false); model.mediaEditing(false) } }
    }
    AndroidView(factory = { web }, modifier = modifier.mediaReveal(ready), update = {})
}
