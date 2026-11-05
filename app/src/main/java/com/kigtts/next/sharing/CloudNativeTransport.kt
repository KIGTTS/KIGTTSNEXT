package com.kigtts.next.sharing

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kigtts.next.media.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.plugins.pluginBrowserDocument
import kotlinx.coroutines.*
import org.json.JSONObject

/** A nonvisual transport runtime. It has no page navigation or user-facing HTML. */
@Composable internal fun CloudNativeTransport(model: AppViewModel, session: CloudNativeSession, receiving: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val document = remember(session) { pluginBrowserDocument(model, session.panel.owner, session.panel.pageId, session.panel.block) }
    var initialized by remember(session) { mutableStateOf(false) }
    val enabled by rememberUpdatedState(receiving)
    val web = remember(session) { createBrowserView(context, document, ready = {}, error = {
        session.event(JSONObject().put("type", "status").put("text", "暂时无法连接，请重试"))
    }) }
    val transfer = remember(web) { WebSharingTransfer(model, session.panel.owner, web, scope, { true }, session::completed).also { it.install() } }
    DisposableEffect(web) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            session.event(JSONObject().put("type", "status").put("text", "请更新 Android System WebView 后重试"))
            return@DisposableEffect onDispose { web.destroy(); session.close() }
        }
        var closed = false
        session.command = { if (!closed) web.evaluateJavascript("window.cloudCommand&&window.cloudCommand(${it});", null) }
        WebViewCompat.addWebMessageListener(web, "KigttsCloudNative", setOf(WebContentPolicy.ORIGIN)) { _, message, origin, main, _ ->
            if (closed || !main || origin.toString() != WebContentPolicy.ORIGIN || message.data.orEmpty().length > 128*1024) return@addWebMessageListener
            runCatching { JSONObject(message.data.orEmpty()) }.getOrNull()?.let { event ->
                if (event.optString("type") == "ready" && !initialized) {
                    initialized = true
                    scope.launch {
                        val person = model.identities.current
                        val profile = JSONObject().put("nickname", person?.nickname?.ifBlank { "KIGTTS 用户" } ?: "KIGTTS 用户")
                            .put("avatarFrame", person?.avatarFrame ?: "circle")
                        val avatar = withContext(Dispatchers.IO) { person?.avatar?.let(model.stickers::imageFile)?.let(::faceAvatarData) }
                        if (avatar != null) profile.put("avatar", "data:image/jpeg;base64,$avatar")
                        session.command(JSONObject().put("op", "init").put("profile", profile).put("deviceKey", model.sharing.deviceKey).put("receiving", enabled))
                    }
                } else runCatching { session.event(event) }
            }
        }
        web.loadUrl(document.url)
        onDispose {
            session.command(JSONObject().put("op", "close")); closed = true; session.command = {}
            transfer.close(); web.stopLoading(); web.destroy(); session.close()
        }
    }
    LaunchedEffect(receiving, initialized) { if (initialized) session.command(JSONObject().put("op", "receiving").put("value", receiving)) }
    AndroidView(factory = { web }, modifier = Modifier.size(1.dp).alpha(0f).clearAndSetSemantics {})
}
