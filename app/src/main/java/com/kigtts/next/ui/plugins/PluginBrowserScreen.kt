package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.media.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.plugin.api.*

internal fun pluginBrowserDocument(model: AppViewModel, owner: String, pageId: String, block: PageBlock.Browser) =
    pluginBrowserDocument(model.pluginManager, owner, pageId, block)

internal fun pluginBrowserDocument(manager: com.kigtts.plugin.runtime.PluginManager, owner: String, pageId: String, block: PageBlock.Browser): BrowserDocument {
    require(block.root?.pluginId == null || block.root?.pluginId == owner) { "网页资源不属于此插件" }
    val capabilities = manager.status.firstOrNull { it.installed.manifest.id == owner }?.installed?.manifest?.capabilities.orEmpty()
    require(!(block.allowNetwork || block.url != null) || "network" in capabilities) { "插件未声明network能力" }
    val root = block.root?.let { manager.paths.storage(owner).file(it.path) }
    if (root != null) require(root.isDirectory && WebContentPolicy.file(root, block.mainPage) != null) { "网页主文件不存在" }
    val url = block.url?.also { require(WebContentPolicy.https(it)) } ?: (WebContentPolicy.ORIGIN + "/content/" + Uri.encode(block.mainPage, "/"))
    return BrowserDocument("plugin:$owner:$pageId", url, root, manager.paths.storage(owner).file("cache/browser/$pageId"), block.allowNetwork || block.url != null, block.allowedActions)
}
@Composable internal fun PluginBrowserScreen(model: AppViewModel, owner: String, pageId: String, block: PageBlock.Browser,
    contextUpdate: String? = null, transparentBackground: Boolean = false) {
    val context = LocalContext.current; val active = LocalPageActive.current
    val scope = rememberCoroutineScope()
    val currentActive by rememberUpdatedState(active)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val webTheme by WebThemeState.themes.collectAsState()
    val latestContext by rememberUpdatedState(contextUpdate)
    var ready by remember(owner, pageId, block) { mutableStateOf(false) }
    val checked = remember(owner, pageId, block) { runCatching { pluginBrowserDocument(model, owner, pageId, block) } }
    val document = checked.getOrNull()
    if (document == null) { androidx.compose.material3.Text(uiText("plugin.preview_page_unavailable")); return }
    val gate = remember(document) { WebNetworkGate(model.pluginManager.permissions, model.pluginManager.permissionOwner(owner), model.registry.plugin(owner)?.descriptor?.name ?: owner,
        { currentActive }, model.pluginManager.status.firstOrNull { it.installed.manifest.id == owner }?.installed?.manifest?.permissionPurposes?.get("network") ?: "加载此插件网页所需的外部资源。") }
    val web = remember(document) { createBrowserView(context, document.copy(authorizeNetwork = gate::authorize, networkGranted = gate::granted), ready = { view ->
        ready = true; latestContext?.let { view.evaluateJavascript(it, null) }
    }, error = {}, action = { action, inputs -> if (currentActive) model.invoke(owner, action, inputs) }).apply {
        if (transparentBackground) setBackgroundColor(android.graphics.Color.TRANSPARENT)
        if (document.root != null) { installKigttsHaptics { currentActive }; if (document.network) installNetworkConsent(scope, gate::authorize) }
    } }
    val devices = remember(web) { WebPagePrivileges(web, model.pluginManager.permissions, "plugin:$owner:$pageId",
        model.registry.plugin(owner)?.descriptor?.name ?: owner, scope, { currentActive }, gate::authorize).also { it.install() } }
    val sharing = remember(web) { if (document.root != null) com.kigtts.next.sharing.WebSharingTransfer(model, owner, web, scope, active = { currentActive }).also { it.install() } else null }
    LaunchedEffect(web) { web.loadUrl(document.url) }
    LaunchedEffect(web, ready, contextUpdate) { if (ready) contextUpdate?.let { web.evaluateJavascript(it, null) } }
    LaunchedEffect(webTheme, ready) { if (ready) web.applyKigttsTheme() }
    LaunchedEffect(active) { if (active) { web.onResume(); devices.resume() } else { devices.pause(); web.onPause() } }
    DisposableEffect(web, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { devices.pause(); web.onPause() } else if (event == Lifecycle.Event.ON_START && currentActive) { web.onResume(); devices.resume() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); sharing?.close(); devices.close(); web.stopLoading(); web.destroy() }
    }
    AndroidView(factory = { web }, modifier = Modifier.fillMaxSize().testTag("plugin-browser"))
}
