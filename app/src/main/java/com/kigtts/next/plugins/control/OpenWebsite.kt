package com.kigtts.next.plugins.control

import com.kigtts.next.links.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal suspend fun openWebsite(manager: PluginManager, model: AppViewModel?, owner: String, capabilities: Set<String>, command: AppCommand.OpenWeb): AppReply {
    if (model == null) return AppReply.Failure("HOST_UNAVAILABLE", "请先打开 KIGTTS")
    if ("network" !in capabilities) return AppReply.Failure("PERMISSION_DENIED", "插件需要声明网络权限")
    return try {
        TrustedWebsite.validate(command.url)
        val mode = WebOpenMode.entries.firstOrNull { it.wire == command.mode } ?: return AppReply.Failure("INVALID", "不支持这个打开方式")
        if (!manager.requestPermission(owner, "network")) return AppReply.Failure("PERMISSION_DENIED", "网络权限尚未授权")
        val accepted = withContext(Dispatchers.Main) { model.externalLinks.offer(ExternalLink(command.url, title = command.title.take(120), mode = mode, add = command.addToQuickText)) }
        if (!accepted) return AppReply.Failure("BUSY", "请先处理当前链接")
        AppReply.Data(buildJsonObject { put("accepted", true); put("pending", true) })
    } catch (cancel: CancellationException) { throw cancel }
    catch (e: Exception) { AppReply.Failure("INVALID", e.message ?: "无法打开这个链接") }
}
