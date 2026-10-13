package com.kigtts.tools.agent

import com.kigtts.plugin.api.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

data class ToolResult(val content: String, val reviewToken: String? = null, val pause: Boolean = false,
    val summary: String? = null, val reviewKind: ReviewKind = ReviewKind.QUICK_TEXT, val transactionId: String? = null)

/** Data contracts are decoded by the SDK, so calls cannot invent owners or bypass host permissions. */
class AgentTools(private val environment: PluginEnvironment) {
    private val stickers = AgentStickerTools(environment, ::resource, ::scopedPath)
    val schemas: JsonArray = buildJsonArray {
        add(tool("app", "控制软件。先通过type=describe获取可用设置键和导航目标。type取describe/state/navigate/text/automatic-read/speak/stop-speech/setting/plugin-action。command按SDK JSON格式包含type及对应字段；只能调用插件明确开放的action。", "command", objectSchema()))
        add(tool("quick_text", "管理快捷文本和HTML资源。先describe/query取得revision。command.type可用describe/query/prepare/commit/discard/transaction/publish-page/publish-media/publish-audio/files/get-file/put-file/set-main/presentation/edit-files/export/import/open-ui。prepare需要transactionId/revision/edits，edit.type取group.put/entry.put/entry.remove/group.remove/entry.move/entry.order/group.order。需要确认时等待用户；不要重试已经执行的变更。", "command", objectSchema()))
        add(tool("stickers", STICKER_TOOL_DESCRIPTION, "command", objectSchema()))
        add(tool("write_file", "在本插件workspace/下写UTF-8文本（HTML/JS/CSS等）。不可访问密钥、其它插件或宿主文件。返回PluginResource可用于publish-page。", "path", stringSchema(), "text" to stringSchema()))
        add(tool("read_file", "读取本插件workspace/、drafts/、imports/或exports/中的文本。", "path", stringSchema()))
        add(tool("list_files", "列出本插件workspace/、drafts/、imports/或exports/中的文件。", "path", stringSchema()))
        add(tool("draft_lua", "创建或更新Lua插件草稿。manifest={id,name,version,permissions:[app-control/quick-text-read/quick-text-write/stickers-read/stickers-write子集]}。main.lua return表有contributions(ctx),pages(ctx),execute(action,ctx)。各函数返回SDK JSON对应Lua table，blocks.type=text/actions/group等，outcome.type=message/text等。host.app/host.quick/host.stickers调用SDK command表，host.data.read/write只操作脚本自身data，host.asset.read只读自身assets。source为main.lua源码。只生成草稿，不安装。", "manifest", objectSchema(), "source" to stringSchema()))
        add(tool("deliver_lua", "将draft_lua返回的path送到runtime.lua的import入口，运行时会校验并让用户审核。返回pending/accepted意味着尚未安装，必须等待用户。", "path", stringSchema()))
    }
    suspend fun execute(call: ToolCall): ToolResult {
        val args = PluginJson.parseToJsonElement(call.arguments).jsonObject
        return when (call.name) {
            "app" -> {
                val command = PluginJson.decodeFromJsonElement<AppCommand>(args.getValue("command"))
                // Resource installation has its own explicit path and review workflow.
                require(command !is AppCommand.DeliverResource) { "请使用 deliver_lua 送交脚本审核" }
                val reply = requireNotNull(environment.appBroker) { "宿主未授予操作能力" }.request(command)
                ToolResult(PluginJson.encodeToString(reply))
            }
            "quick_text" -> {
                val command = PluginJson.decodeFromJsonElement<QuickTextCommand>(args.getValue("command"))
                validateResources(command)
                val reply = requireNotNull(environment.quickTextBroker) { "宿主未授予快捷文本能力" }.request(command)
                val token = (reply as? QuickTextReply.Failure)?.takeIf { it.code == QuickTextError.CONFIRMATION_REQUIRED }?.reviewToken
                    ?: (command as? QuickTextCommand.Commit)?.token?.takeIf { reply is QuickTextReply.Failure && reply.code == QuickTextError.CONFIRMATION_REQUIRED }
                ToolResult(PluginJson.encodeToString(reply), token, token != null)
            }
            "stickers" -> stickers.execute(args.getValue("command"))
            "write_file" -> {
                val path = args.string("path"); require(path.startsWith("workspace/"))
                val text = args.string("text"); require(text.toByteArray(Charsets.UTF_8).size <= 512 * 1024)
                val file = environment.storage.file(path); file.parentFile?.mkdirs(); file.writeText(text, Charsets.UTF_8)
                ToolResult(buildJsonObject { put("resource", PluginJson.encodeToJsonElement(PluginResource(path))); put("bytes", file.length()) }.toString())
            }
            "read_file" -> {
                val file = environment.storage.file(scopedPath(args.string("path")))
                require(file.isFile && file.length() <= 256 * 1024)
                ToolResult(buildJsonObject { put("text", file.readText(Charsets.UTF_8)) }.toString())
            }
            "list_files" -> {
                val file = environment.storage.file(scopedPath(args.string("path")))
                ToolResult(buildJsonArray {
                    file.listFiles()?.take(200)?.forEach { item -> add(buildJsonObject {
                        put("name", item.name); put("directory", item.isDirectory); put("bytes", if (item.isFile) item.length() else 0)
                    }) }
                }.toString())
            }
            "draft_lua" -> draft(args)
            "deliver_lua" -> {
                val path = args.string("path"); require(path.startsWith("drafts/"))
                require(environment.storage.file("$path/manifest.json").isFile && environment.storage.file("$path/main.lua").isFile)
                val reply = requireNotNull(environment.appBroker).request(AppCommand.DeliverResource("runtime.lua", PluginResource(path), "import"))
                val pending = reply is AppReply.Data && reply.value.let { it["pending"]?.jsonPrimitive?.booleanOrNull == true || it["accepted"]?.jsonPrimitive?.booleanOrNull == true }
                ToolResult(PluginJson.encodeToString(reply), pause = pending, reviewKind = ReviewKind.EXTERNAL)
            }
            else -> ToolResult("{\"error\":\"UNKNOWN_TOOL\"}")
        }
    }
    private fun draft(args: JsonObject): ToolResult {
        val manifest = args.getValue("manifest").jsonObject
        val id = manifest.string("id"); require(id.matches(Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+")))
        require(manifest.string("name").length in 1..64)
        require(manifest.string("version").length in 1..32)
        val permissions = manifest["permissions"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        require(permissions.all { it in setOf("app-control", "quick-text-read", "quick-text-write", "stickers-read", "stickers-write") })
        val source = args.string("source"); require(source.toByteArray(Charsets.UTF_8).size <= 256 * 1024)
        val path = "drafts/$id"
        environment.storage.directory(path)
        environment.storage.file("$path/manifest.json").writeText(manifest.toString(), Charsets.UTF_8)
        environment.storage.file("$path/main.lua").writeText(source, Charsets.UTF_8)
        return ToolResult(buildJsonObject { put("path", path); put("installed", false) }.toString())
    }
    private fun scopedPath(path: String): String {
        require(listOf("workspace", "drafts", "imports", "exports", "resources/quick-imports").any { path == it || path.startsWith("$it/") }) { "文件不在助手工作目录" }
        environment.storage.file(path); return path
    }
    private fun resource(source: PluginResource) {
        require(source.pluginId == null || source.pluginId == "tools.cloud-agent") { "资源不属于此助手" }
        scopedPath(source.path)
    }
    private fun validateResources(command: QuickTextCommand) {
        when (command) {
            is QuickTextCommand.PublishPage -> resource(command.root)
            is QuickTextCommand.PublishAudio -> resource(command.source)
            is QuickTextCommand.PutFile -> resource(command.source)
            is QuickTextCommand.Import -> resource(command.source)
            is QuickTextCommand.Export -> scopedPath(command.destination)
            is QuickTextCommand.GetFile -> scopedPath(command.destination)
            is QuickTextCommand.EditFiles -> command.edits.filterIsInstance<QuickFileEdit.Put>().forEach { resource(it.source) }
            is QuickTextCommand.PublishMedia -> when (val content = command.content) {
                is SubtitleContent.Image -> { resource(content.source); content.motionVideo?.let(::resource) }
                is SubtitleContent.Video -> resource(content.source)
                is SubtitleContent.Html -> content.source?.let(::resource)
                else -> Unit
            }
            else -> Unit
        }
    }
}
internal fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
private fun stringSchema() = buildJsonObject { put("type", "string") }
private fun objectSchema() = buildJsonObject { put("type", "object") }
private fun tool(name: String, description: String, required: String, schema: JsonObject, vararg extra: Pair<String, JsonObject>) = buildJsonObject {
    put("type", "function"); put("function", buildJsonObject {
        put("name", name); put("description", description); put("parameters", buildJsonObject {
            put("type", "object"); put("properties", buildJsonObject { put(required, schema); extra.forEach { put(it.first, it.second) } })
            put("required", buildJsonArray { add(required); extra.forEach { add(it.first) } })
            put("additionalProperties", false)
        })
    })
}
