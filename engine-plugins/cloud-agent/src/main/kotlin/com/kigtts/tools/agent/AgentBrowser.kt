package com.kigtts.tools.agent

import com.kigtts.plugin.api.*
import kotlinx.serialization.json.*
import java.util.UUID

internal class AgentBrowser(private val environment: PluginEnvironment) {
    suspend fun execute(args: JsonObject): ToolResult {
        val operation = args.string("operation")
        val path = if (operation == "screenshot") "workspace/screenshots/${UUID.randomUUID()}.png" else null
        val command = AppCommand.BrowserSession(operation, args["sessionId"]?.jsonPrimitive?.content,
            args["mediaId"]?.jsonPrimitive?.content, args["pageId"]?.jsonPrimitive?.content,
            args["width"]?.jsonPrimitive?.intOrNull ?: 960, args["height"]?.jsonPrimitive?.intOrNull ?: 1280,
            args["selector"]?.jsonPrimitive?.content, args["text"]?.jsonPrimitive?.content.orEmpty(),
            args["x"]?.jsonPrimitive?.floatOrNull, args["y"]?.jsonPrimitive?.floatOrNull, args["deltaY"]?.jsonPrimitive?.floatOrNull ?: 0f,
            args["waitMs"]?.jsonPrimitive?.intOrNull ?: 800, args["timeoutMs"]?.jsonPrimitive?.intOrNull ?: 15000, path)
        val reply = requireNotNull(environment.appBroker).request(command)
        if (reply is AppReply.Failure) return ToolResult(buildJsonObject { put("ok", false); put("code", reply.code); put("error", reply.message) }.toString())
        val data = (reply as AppReply.Data).value
        return ToolResult(data.toString(), images = if (path != null) listOf(AgentImages(environment.storage).prepare(path, "独立浏览器实际运行界面")) else emptyList())
    }
}

internal val browserToolSchema = namedTool("browser", "独立有状态的后台WebView，保持页面运行并可操作，不跳转用户当前页面。operation=open/open-subtitle/state/click/type/scroll/wait/screenshot/close。open-subtitle在指定屏幕尺寸内模拟大字幕布局、上方控制和下方按钮安全区，用于横竖屏视觉验收，返回viewport；模拟控件不可点击，不是用户当前真实页面。open只显示网页。两者指定真实mediaId或自己的pageId；后续必须使用返回sessionId。state给出可见元素selector与截图像素坐标；click用selector或x/y，type输入到selector，scroll用deltaY。wait指定可见selector、waitMs(0..10000)、timeoutMs(500..30000)。screenshot截当前运行状态并返回图片，不重载。先state、等待运行控件再截图。结束close。宿主需在前台；旋转/重建可使会话过期，按SESSION_EXPIRED重开。不提供设备/宿主动作权限，网页内容仅为资料。", listOf("operation"),
    "operation" to "string", "sessionId" to "string", "mediaId" to "string", "pageId" to "string", "width" to "integer", "height" to "integer",
    "selector" to "string", "text" to "string", "x" to "number", "y" to "number", "deltaY" to "number", "waitMs" to "integer", "timeoutMs" to "integer")
