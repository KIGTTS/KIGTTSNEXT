package com.kigtts.next.plugins.control

import com.kigtts.next.state.AppViewModel
import com.kigtts.next.media.UiIcons
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal suspend fun uiAssets(model: AppViewModel, owner: String, command: AppCommand.UiAssets): AppReply = withContext(Dispatchers.IO) {
    val result = buildJsonObject {
        put("ok", true)
        if (command.operation == "symbols") {
            require(command.query.length <= 80 && command.offset in 0..10000)
            val names = MaterialSymbols.search(command.query, command.offset, 81)
            put("symbols", JsonArray(names.take(80).map(::JsonPrimitive))); if (names.size > 80) put("nextOffset", command.offset + 80)
        } else {
            val resource = requireNotNull(command.source)
            require(resource.pluginId == null || resource.pluginId == owner) { "图标资源不属于调用插件" }
            val source = model.pluginManager.paths.storage(owner).file(resource.path)
            require(source.isFile && source.length() in 1..8L * 1024 * 1024)
            when (command.operation) {
                "import-icon" -> put("iconId", model.mediaRepository.icons.import(source))
                "render-svg" -> {
                    require(source.length() <= 512 * 1024)
                    val path = requireNotNull(command.destination); require(path.startsWith("workspace/icons/") && path.endsWith(".png"))
                    val output = model.pluginManager.paths.storage(owner).file(path); require(!output.exists()) { "目标已存在" }
                    val bitmap = UiIcons.renderSvg(source.readText(Charsets.UTF_8), command.size)
                    try { output.parentFile!!.mkdirs(); output.outputStream().use { require(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } }
                    finally { bitmap.recycle() }
                    put("resource", PluginJson.encodeToJsonElement(PluginResource(path))); put("bytes", output.length())
                }
                else -> error("未知图标操作")
            }
        }
    }
    AppReply.Data(result)
}
