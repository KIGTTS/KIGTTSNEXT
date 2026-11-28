package com.kigtts.next.ui.media

import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.navigation.LocalPageActive
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun MediaWeb(entry: MediaEntry, state: AppState, model: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current; val active = LocalPageActive.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    val currentActive by rememberUpdatedState(active)
    val phraseDraft by model.content.phraseDraft.collectAsStateWithLifecycle()
    val draft = phraseDraft?.takeIf { it.mediaId == entry.id }
    val entryKey = if (state.destination is Destination.HtmlPreview && draft != null) draft.phraseId ?: draft.key else state.webPermissionEntryId ?: "media:${entry.id}"
    val grantOwner = WebNetworkGate.owner(entryKey)
    val devices = remember(entry.id, grantOwner) { arrayOfNulls<WebPagePrivileges>(1) }
    val networkGate = remember(entry.id, grantOwner) { WebNetworkGate(model.pluginManager.permissions, grantOwner, draft?.title?.ifBlank { draft.text }?.take(60) ?: entry.name, active = { currentActive }) }
    val safeArea by rememberUpdatedState(LocalMediaSafeArea.current)
    var destroyed by remember(entry.id) { mutableStateOf(false) }
    var viewport by remember(entry.id) { mutableStateOf(IntSize.Zero) }
    val safeApplier = remember(entry.id) { WebSafeAreaApplier() }
    fun applySafeArea(view: WebView, capture: Boolean = false, force: Boolean = false) {
        if (destroyed || viewport.width <= 0 || viewport.height <= 0) return
        safeApplier.apply(view, safeArea, viewport, force || capture) {}
    }
    fun ownsPage()=currentActive&&model.state.value.mediaId==entry.id
    var ready by remember(entry.id) { mutableStateOf(false) }
    val web = remember(entry.id, grantOwner) {
        WebView(context).apply {
            WebCookies.enable()
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            settings.disabledActionModeMenuItems = WebSettings.MENU_ITEM_PROCESS_TEXT or WebSettings.MENU_ITEM_WEB_SEARCH or WebSettings.MENU_ITEM_SHARE
            settings.javaScriptEnabled = true; settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode = WebSettings.LOAD_NO_CACHE; settings.domStorageEnabled = false
            settings.mediaPlaybackRequiresUserGesture = true
            val source = model.mediaRepository.file(entry)
            val cache = if (entry.pluginId == null) File(model.mediaRepository.root, "web-cache/" + entry.id)
                else model.pluginManager.paths.storage(entry.pluginId).file("cache/web/" + entry.id)
            val localRoot = if(entry.kind==MediaKind.HTML && entry.pluginId==null) model.mediaRepository.htmlFiles.root(entry.id)
                else if(entry.pluginId!=null&&entry.projectRoot!=null)model.pluginManager.paths.storage(entry.pluginId).file(entry.projectRoot) else source?.parentFile
            val phrase = state.webPermissionEntryId?.takeIf { state.destination == Destination.Home }
            val presentation = phrase?.let {
                WebPresentationSession(context, entry.id, it, localRoot.takeIf { entry.kind == MediaKind.HTML },
                    model.viewModelScope, { owner, enabled -> model.setWebFullscreen(owner, entry.id, it, enabled) },
                    model::updateWebPresentation)
            }
            val loader = WebResourceLoader(cache, localRoot, true,
                pageOrigin = if (entry.kind == MediaKind.WEB) entry.primary else WebContentPolicy.ORIGIN,
                bundledResource = { WebUiResources.response(it, context.assets::open) }, authorizeNetwork = networkGate::authorize, networkGranted = networkGate::granted)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { presentation?.clear() }
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = loader.intercept(request.url.toString(), request.method, request.isForMainFrame)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !(request.url.toString().startsWith(WebContentPolicy.ORIGIN + "/content/") || WebContentPolicy.https(request.url.toString()))
                override fun onPageFinished(view: WebView, url: String) { view.applyKigttsTheme();ready=true;applySafeArea(view, true);if(ownsPage())model.webHistory(view.canGoBack()) }
                override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) { if (ready) applySafeArea(view, force = true) }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) { ready=true;if(ownsPage())model.message(model.hostCopy("web.load_error")) }
                }
            }
            if (entry.kind == MediaKind.HTML && entry.pluginId != null && entry.actions.isNotEmpty()) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(this, "Kigtts", setOf(WebContentPolicy.ORIGIN)) { _, message, origin, mainFrame, _ ->
                        if(ownsPage())WebContentPolicy.action(entry, message.data.orEmpty(), origin.toString(), mainFrame)?.let { (action, inputs) -> model.invoke(entry.pluginId, action, inputs) }
                    }
                } else model.message(model.hostCopy("web.update_webview"))
            }
            if (entry.kind == MediaKind.HTML) installKigttsHaptics { currentActive }
            if (entry.kind == MediaKind.HTML) installNetworkConsent(scope, networkGate::authorize)
            val relative = if(source!=null && localRoot!=null)source.relativeTo(localRoot).invariantSeparatorsPath else ""
            devices[0] = WebPagePrivileges(this, model.pluginManager.permissions, entryKey,
                draft?.title?.ifBlank { draft.text }?.take(60) ?: entry.name, scope, {
                    currentActive && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                        (phrase == null || model.state.value.isWebShortcut(entry.id, phrase))
                }, networkGate::authorize, presentation).also { it.install() }
            installKigttsThemeAtDocumentStart()
            loadUrl(if (entry.kind == MediaKind.WEB) entry.primary else WebContentPolicy.ORIGIN + "/content/" + android.net.Uri.encode(relative,"/"))
        }
    }
    var lastBack by remember { mutableIntStateOf(state.webBackRevision) }
    LaunchedEffect(state.webBackRevision) { if (ownsPage()&&lastBack != state.webBackRevision) { lastBack = state.webBackRevision; if (web.canGoBack()) web.goBack(); model.webHistory(web.canGoBack()) } }
    LaunchedEffect(ime, active) { if(ownsPage())model.mediaEditing(ime > 0 && web.hasFocus()) }
    LaunchedEffect(active) { if (active) { web.onResume(); devices[0]?.resume() } else { devices[0]?.pause(); web.onPause() } }
    val webTheme by WebThemeState.themes.collectAsStateWithLifecycle()
    LaunchedEffect(webTheme, ready) { if (ready) web.applyKigttsTheme() }
    LaunchedEffect(LocalMediaSafeArea.current, viewport) { if (ready) applySafeArea(web) }
    DisposableEffect(web, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { devices[0]?.pause(); web.onPause();
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { CookieManager.getInstance().flush() }
        } else if (event == Lifecycle.Event.ON_START && currentActive) { web.onResume(); devices[0]?.resume() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); destroyed = true; devices[0]?.close(); web.stopLoading(); web.destroy(); if(ownsPage()) { model.webHistory(false); model.mediaEditing(false) } }
    }
    AndroidView(factory = { web }, modifier = modifier.mediaReveal(ready).testTag("media-web").onSizeChanged { viewport = it }, update = {})
    DisposableEffect(web) {
        val listener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (ready) applySafeArea(web) }
        web.addOnLayoutChangeListener(listener); onDispose { web.removeOnLayoutChangeListener(listener) }
    }
}
