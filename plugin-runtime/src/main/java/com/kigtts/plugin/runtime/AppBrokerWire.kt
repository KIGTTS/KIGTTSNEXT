package com.kigtts.plugin.runtime

import com.kigtts.plugin.api.*
import kotlinx.coroutines.*

internal class HostAppBroker(private val backend: AppBroker) : IHostAppBroker.Stub() {
    @Volatile private var active = true
    override fun request(command: String): String {
        val reply = if (!active) AppReply.Failure("PERMISSION_DENIED", "插件连接已关闭")
        else if (command.toByteArray().size > 256 * 1024) AppReply.Failure("TOO_LARGE", "请缩小请求")
        else runBlocking(Dispatchers.IO) {
            try { backend.request(PluginJson.decodeFromString(command)) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { AppReply.Failure("INVALID", "操作未完成，请检查输入") }
        }
        return PluginJson.encodeToString<AppReply>(reply).let {
            if (it.toByteArray().size <= 512 * 1024) it else PluginJson.encodeToString<AppReply>(AppReply.Failure("TOO_LARGE", "结果过大，请分页读取"))
        }
    }
    fun close() { active = false }
}
internal class WorkerAppBroker(private val remote: IHostAppBroker) : AppBroker {
    override suspend fun request(command: AppCommand): AppReply = withContext(Dispatchers.IO) {
        val json = PluginJson.encodeToString(command)
        if (json.toByteArray().size > 256 * 1024) AppReply.Failure("TOO_LARGE", "请缩小请求")
        else PluginJson.decodeFromString(remote.request(json))
    }
}
