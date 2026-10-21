package com.kigtts.next.media

import android.content.Context
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.File

/** Shared loader and security settings for plugin pages and actual WebView screenshot inspection. */
data class BrowserDocument(val key: String, val url: String, val root: File?, val cache: File,
    val network: Boolean, val actions: List<String> = emptyList(), val authorizeNetwork: (String) -> Boolean = { true },
    val networkGranted: () -> Boolean = { network })

fun createBrowserView(context: Context, document: BrowserDocument, ready: (WebView) -> Unit,
    error: (String) -> Unit = {}, action: (String, Map<String, String>) -> Unit = { _, _ -> }): WebView = WebView(context).apply {
    WebCookies.enable(); CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
    settings.apply {
        javaScriptEnabled = true; allowFileAccess = false; allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW; cacheMode = WebSettings.LOAD_NO_CACHE
        domStorageEnabled = false; mediaPlaybackRequiresUserGesture = true
    }
    val loader = WebResourceLoader(document.cache, document.root, document.network, pageOrigin = document.url,
        bundledResource = { WebUiResources.response(it, context.assets::open) }, authorizeNetwork = document.authorizeNetwork, networkGranted = document.networkGranted)
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = loader.intercept(request.url.toString(), request.method, request.isForMainFrame)
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
            !(request.url.toString().startsWith(WebContentPolicy.ORIGIN + "/content/") || document.network && WebContentPolicy.https(request.url.toString()))
        override fun onPageFinished(view: WebView, url: String) { view.applyKigttsTheme(); ready(view) }
        override fun onReceivedError(view: WebView, request: WebResourceRequest, problem: WebResourceError) {
            if (request.isForMainFrame) error(problem.description.toString())
        }
        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            error("网页资源HTTP ${response.statusCode}：${request.url.host.orEmpty()}${request.url.path.orEmpty().take(300)}")
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) error("${message.sourceId()}:${message.lineNumber()}: ${message.message().take(1000)}")
            return true
        }
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(this, "document.addEventListener('DOMContentLoaded',()=>{" + WebThemeState.current.script() + "},{once:true});", setOf("*"))
    }
    if (document.root != null && document.actions.isNotEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(this, "Kigtts", setOf(WebContentPolicy.ORIGIN)) { _, message, origin, mainFrame, _ ->
            runCatching {
                require(mainFrame && origin.toString() == WebContentPolicy.ORIGIN)
                val raw = message.data.orEmpty(); require(raw.length <= 64 * 1024)
                val value = JSONObject(raw); val id = value.getString("action"); require(id in document.actions)
                val inputs = value.optJSONObject("inputs") ?: JSONObject(); require(inputs.length() <= 32)
                val map = inputs.keys().asSequence().associateWith { key -> require(key.length in 1..128 && !key.startsWith('_')); (inputs.get(key) as String).also { require(it.length <= 4096) } }
                action(id, map)
            }
        }
    }
}
