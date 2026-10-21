package com.kigtts.next.media

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.*

/** CSP covers sockets/fetch which WebView resource interception alone cannot police. */
fun WebView.installNetworkConsent(scope: CoroutineScope, authorize: (String) -> Boolean) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    var requesting = false
    WebViewCompat.addWebMessageListener(this, "KigttsNetworkConsent", setOf(WebContentPolicy.ORIGIN)) { view, message, origin, mainFrame, _ ->
        val url = message.data.orEmpty().replaceFirst("wss://", "https://")
        if (mainFrame && origin.toString() == WebContentPolicy.ORIGIN && url.length <= 4000 && WebContentPolicy.https(url) && !requesting) {
            requesting = true
            scope.launch {
                try { if (withContext(Dispatchers.IO) { authorize(url) }) view.reload() }
                finally { requesting = false }
            }
        }
    }
    WebViewCompat.addDocumentStartJavaScript(this, """
        document.addEventListener('securitypolicyviolation',e=>{
          if((e.violatedDirective.startsWith('connect-src')||e.violatedDirective==='form-action')&&/^(https|wss):\/\//.test(e.blockedURI))
            KigttsNetworkConsent.postMessage(e.blockedURI);
        });
    """.trimIndent(), setOf(WebContentPolicy.ORIGIN))
}
