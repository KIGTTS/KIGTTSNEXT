package com.kigtts.next.plugins.control

import android.net.Uri
import com.kigtts.next.media.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.plugins.pluginBrowserDocument
import com.kigtts.plugin.api.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import java.io.File

internal suspend fun browserDocument(model: AppViewModel, owner: String, capabilities: Set<String>, selectedMedia: String?, selectedPage: String?): BrowserDocument {
    require((selectedMedia != null) != (selectedPage != null)) { "指定一个mediaId或本插件pageId" }
    val pageId = selectedPage
    return if (pageId != null) {
        val page = model.registry.plugin(owner)?.page(pageId, model.context()) ?: error("没有此插件页面")
        val browser = page.blocks.filterIsInstance<PageBlock.Browser>().singleOrNull() ?: error("此插件页面不是浏览器页面")
        val allowed = { model.pluginManager.permissions.access(model.pluginManager.permissionOwner(owner), "network") == com.kigtts.plugin.runtime.PermissionAccess.ALLOW }
        pluginBrowserDocument(model, owner, pageId, browser).copy(authorizeNetwork = { allowed() }, networkGranted = allowed)
    } else {
        require("quick-text-read" in capabilities || "quick-text-write" in capabilities) { "没有页面读取能力" }
        val entry = model.mediaRepository.get(requireNotNull(selectedMedia)) ?: error("没有此网页附件")
        require(entry.kind in setOf(MediaKind.HTML, MediaKind.WEB)) { "附件不是网页" }
        val source = model.mediaRepository.file(entry)
        val local = if (entry.kind == MediaKind.WEB) null else if (entry.pluginId == null) model.mediaRepository.htmlFiles.root(entry.id)
            else entry.projectRoot?.let { model.pluginManager.paths.storage(entry.pluginId).file(it) } ?: source?.parentFile
        val url = if (entry.kind == MediaKind.WEB) entry.primary else WebContentPolicy.ORIGIN + "/content/" + Uri.encode(requireNotNull(source).relativeTo(requireNotNull(local)).invariantSeparatorsPath, "/")
        val cards = model.phrases.value.filter { it.mediaId == entry.id }
        val key = cards.singleOrNull()?.id ?: "media:${entry.id}"
        val allowed = { model.pluginManager.permissions.access(WebNetworkGate.owner(key), "network") == com.kigtts.plugin.runtime.PermissionAccess.ALLOW }
        BrowserDocument("media:" + entry.id, url, local, model.pluginManager.paths.storage(owner).file("cache/inspection"), true,
            authorizeNetwork = { allowed() }, networkGranted = allowed)
    }
 }

internal suspend fun sessionBrowser(model: AppViewModel, owner: String, capabilities: Set<String>, command: AppCommand.BrowserSession): AppReply {
    val document = if (command.operation in setOf("open", "open-subtitle")) browserDocument(model, owner, capabilities, command.mediaId, command.pageId) else null
    val path = command.destination
    val output = if (command.operation == "screenshot") {
        require(path != null && path.startsWith("workspace/screenshots/")) { "截图只能写入调用插件的截图目录" }
        model.pluginManager.paths.storage(owner).file(path)
    } else null
    val result = BrowserSessions.run(owner, command, document, output)
    if (output != null) result.put("resource", org.json.JSONObject(PluginJson.encodeToString(PluginResource(requireNotNull(path), owner))))
    return AppReply.Data(PluginJson.parseToJsonElement(result.toString()).jsonObject)
}

internal suspend fun captureBrowser(model: AppViewModel, owner: String, capabilities: Set<String>, command: AppCommand.BrowserCapture): AppReply {
    require(command.destination.startsWith("workspace/screenshots/")) { "截图只能保存在调用插件的workspace/screenshots/" }
    require((command.mediaId != null) != (command.pageId != null)) { "指定一个mediaId或本插件pageId" }
    val document = browserDocument(model, owner, capabilities, command.mediaId, command.pageId)
    val file = model.pluginManager.paths.storage(owner).file(command.destination)
    val captured = BrowserInspector.capture(model.getApplication(), document, file, command.width, command.height)
    return AppReply.Data(buildJsonObject {
        put("ok", true); put("resource", PluginJson.encodeToJsonElement(PluginResource(command.destination, owner)))
        put("mime", "image/png"); put("width", captured.width); put("height", captured.height)
        put("diagnostics", buildJsonArray { captured.diagnostics.forEach { add(it) } })
        put("scope", "webview-viewport")
    })
}
