package com.kigtts.next.plugins.control

import com.kigtts.next.state.*
import com.kigtts.next.theme.*
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Formal, versioned app operations for user-installed agents. No arbitrary preference/file access. */
class AppControlHost(private val manager: PluginManager, private val ui: () -> AppViewModel?) {
    private val notifications = com.kigtts.next.notifications.PluginNotifications(manager)
    private val overlay = com.kigtts.next.overlay.PluginOverlayHost(manager)
    init { manager.onNotificationsUnavailable = { owner -> notifications.cancelAll(owner); overlay.removeOwner(owner) } }
    fun forPlugin(owner: String, capabilities: Set<String>): AppBroker {
        manager.paths.validateId(owner)
        return object : AppBroker {
            override suspend fun request(command: AppCommand): AppReply {
                if (command is AppCommand.OpenWeb) return openWebsite(manager, ui(), owner, capabilities, command)
                if (command is AppCommand.Overlay) return overlay.request(owner, capabilities, command)
                if (command is AppCommand.Notifications) return notifications.request(owner, capabilities, command)
                if (command is AppCommand.Permission) {
                    val projectId = command.projectId
                    if (projectId != null) return managedPermission(manager, owner, capabilities, projectId, command.permission, ui()?.agentAccess)
                    val allowed = manager.requestPermission(owner, command.permission)
                    return data { put("allowed", allowed) }
                }
                if (command is AppCommand.ManagedCall) return managedCall(manager, ui(), owner, capabilities, command)
                if (command is AppCommand.UiAssets) return try { uiAssets(requireNotNull(ui()), owner, command) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (failure: Exception) { AppReply.Failure("INVALID", failure.message?.take(300) ?: "图标操作失败") }
                if ("app-control" !in capabilities) return AppReply.Failure("PERMISSION_DENIED", "插件没有应用控制权限")
                val installedCaller = manager.status.any { it.installed.manifest.id == owner } || withContext(Dispatchers.IO) { manager.packages.installed().any { it.manifest.id == owner } }
                if (installedCaller && !manager.requestPermission(owner, "app-control"))
                    return AppReply.Failure("PERMISSION_DENIED", "应用控制权限尚未授权或已禁用")
                return try {
                    val readsMedia = command is AppCommand.BrowserCapture && command.mediaId != null || command is AppCommand.BrowserSession && command.operation in setOf("open", "open-subtitle") && command.mediaId != null
                    if (readsMedia && installedCaller && !manager.requestPermission(owner, "quick-text-read")) return AppReply.Failure("PERMISSION_DENIED", "网页读取权限尚未授权")
                    if (command is AppCommand.BrowserSession) return withContext(Dispatchers.Main) { sessionBrowser(requireNotNull(ui()) { "请先打开KIGTTS" }, owner, capabilities, command) }
                    if (command is AppCommand.BrowserCapture) return withContext(Dispatchers.Main) { captureBrowser(requireNotNull(ui()) { "请先打开KIGTTS" }, owner, capabilities, command) }
                    if (command is AppCommand.DeliverResource) return deliver(owner, command)
                    if (command is AppCommand.ResourcePackage) return requireNotNull(ui()).resourceInstalls.request(owner, command)
                    if (command is AppCommand.PluginCall) return call(owner, command)
                    withContext(Dispatchers.Main) { perform(owner, command) }
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: IllegalArgumentException) { AppReply.Failure("INVALID", error.message ?: "参数不合法") }
                catch (error: Exception) { AppReply.Failure("UNAVAILABLE", error.message ?: "操作未完成") }
            }
        }
    }
    private fun perform(owner: String, command: AppCommand): AppReply {
        if (command == AppCommand.Describe) return data {
            put("protocol", 1)
            put("operations", JsonArray(listOf("state", "navigate", "text", "automatic-read", "speak", "stop-speech", "setting", "plugin-action", "deliver-resource", "browser-capture", "browser-session", "overlay").map(::JsonPrimitive)))
            put("navigation", JsonArray(listOf("home", "settings", "plugins", "quick-text", "media", "sketch", "cards").map(::JsonPrimitive)))
            put("settings", JsonArray(settingKeys.map(::JsonPrimitive)))
            put("plugins", buildJsonArray { manager.registry.plugins.forEach { plugin ->
                add(buildJsonObject { put("id", plugin.descriptor.id); put("name", plugin.descriptor.name)
                    put("version", plugin.descriptor.version); put("kind", "native"); put("enabled", true)
                    manager.status.firstOrNull { it.installed.manifest.id == plugin.descriptor.id }?.installed?.let {
                        put("versionCode", it.manifest.versionCode); put("apiVersion", it.manifest.apiVersion)
                        put("installed", true); put("publisherSha256", it.publisherSha256)
                    }
                    put("actions", JsonArray(if (plugin.descriptor.id == owner) emptyList() else plugin.contributions.automationActions.map(::JsonPrimitive))) })
                plugin.contributions.managedPackages.forEach { item -> add(buildJsonObject {
                    put("id", item.id); put("version", item.version); put("kind", item.kind); put("enabled", item.enabled); put("installed", true); put("runtime", plugin.descriptor.id)
                }) }
            }
                val loaded = manager.registry.plugins.map { it.descriptor.id }.toSet()
                manager.status.filter { it.installed.manifest.id !in loaded }.forEach { status -> val installed = status.installed
                    add(buildJsonObject { put("id", installed.manifest.id); put("name", installed.manifest.name)
                        put("version", installed.manifest.version); put("versionCode", installed.manifest.versionCode)
                        put("apiVersion", installed.manifest.apiVersion); put("kind", "native"); put("enabled", installed.enabled); put("installed", true)
                        put("publisherSha256", installed.publisherSha256) })
                }
            })
        }
        val model = ui() ?: return AppReply.Failure("HOST_UNAVAILABLE", "请先打开 KIGTTS")
        when (command) {
            is AppCommand.OpenWeb -> error("已单独处理")
            is AppCommand.ResourcePackage -> error("已单独处理")
            AppCommand.State -> return data { val state = model.state.value
                put("text", state.editorText); put("transcript", state.transcript); put("automaticRead", state.autoPlayback)
                put("listening", state.listening); put("continuousRecognition", state.continuousRecognition)
                put("speaking", state.speaking); state.mediaId?.let { put("mediaId", it) }
                put("themeMode", state.theme.mode.name); put("themeColor", state.theme.hex)
                put("locale", model.context().locale); put("subtitlePlaceholder", state.subtitlePlaceholder)
            }
            is AppCommand.Navigate -> when (command.target) {
                "home" -> model.navigate(Destination.Home)
                "settings" -> model.navigate(Destination.Settings)
                "plugins" -> model.navigate(Destination.Plugins)
                "quick-text" -> model.editQuickTexts()
                "media" -> model.navigate(Destination.MediaLibrary())
                "sketch" -> model.openSidePage(SidePage.SKETCH)
                "cards" -> model.openSidePage(SidePage.CARDS)
                else -> return AppReply.Failure("INVALID", "没有这个页面")
            }
            is AppCommand.SetText -> { require(command.text.length <= 64_000); model.text(command.text) }
            is AppCommand.SetAutomaticRead -> model.setAutoPlayback(command.enabled)
            AppCommand.Speak -> model.speak()
            AppCommand.StopSpeech -> model.stopSpeechFromUi()
            is AppCommand.Setting -> applySetting(model, command)
            is AppCommand.PluginAction -> {
                val target = authorizedTarget(owner, command.pluginId, command.actionId)
                    ?: return AppReply.Failure("PERMISSION_DENIED", "插件未开放此操作，确认按钮只能由用户操作")
                if (target.descriptor.id in model.state.value.busyPlugins) return AppReply.Failure("BUSY", "插件正在处理操作")
                model.invoke(command.pluginId, command.actionId, command.inputs.filterKeys { !it.startsWith('_') } + ("_callerPlugin" to owner))
                return data { put("accepted", true); put("completed", false) }
            }
            AppCommand.Describe, is AppCommand.DeliverResource, is AppCommand.PluginCall, is AppCommand.BrowserCapture, is AppCommand.BrowserSession, is AppCommand.Permission, is AppCommand.ManagedCall, is AppCommand.UiAssets, is AppCommand.Notifications, is AppCommand.Overlay -> error("已单独处理")
        }
        return data { put("accepted", true) }
    }
    private fun authorizedTarget(owner: String, id: String, action: String): KigPlugin? = manager.registry.plugin(id)?.takeIf {
        id != owner && manager.registry.available(it) && action in it.contributions.automationActions
    }
    internal suspend fun performManaged(owner: String, command: AppCommand): AppReply = withContext(Dispatchers.Main) { perform(owner, command) }
    private suspend fun call(owner: String, command: AppCommand.PluginCall): AppReply {
        val target = authorizedTarget(owner, command.pluginId, command.actionId)
            ?: return AppReply.Failure("PERMISSION_DENIED", "插件没有开放此操作")
        val granted = withContext(Dispatchers.Main) { ui()?.agentAccess?.consume(owner) == true }
        val inputs = command.inputs.filterKeys { !it.startsWith('_') } + mapOf("_callerPlugin" to owner, "_agentApproved" to granted.toString())
        val delegation = command.inputs["project"]?.let { ui()?.agentAccess?.delegate(owner, command.pluginId, it) }
        val result = try { target.execute(command.actionId, withContext(Dispatchers.Main) { ui()?.context() ?: PluginContext("", "") }.copy(inputs = inputs)) }
            finally { delegation?.close() }
        return data { put("completed", true); put("outcome", PluginJson.encodeToJsonElement(PluginOutcome.serializer(), result)) }
    }
    private suspend fun deliver(owner: String, command: AppCommand.DeliverResource): AppReply {
        require(command.source.pluginId == null || command.source.pluginId == owner) { "资源不属于调用插件" }
        if (authorizedTarget(owner, command.pluginId, command.actionId) == null)
            return AppReply.Failure("PERMISSION_DENIED", "目标插件未开放资源导入")
        val canReceive = withContext(Dispatchers.Main) {
            val model = ui() ?: return@withContext false
            command.pluginId !in model.state.value.busyPlugins
        }
        if (!canReceive) return AppReply.Failure("BUSY", "请先打开 KIGTTS，并等待目标插件完成当前操作")
        val relative = withContext(Dispatchers.IO) {
            ScopedResourceDelivery.copy(manager.paths.storage(owner), manager.paths.storage(command.pluginId), command.source.path)
        }
        if (command.actionId == "agent-install") {
            val allowed = withContext(Dispatchers.Main) { ui()?.agentAccess?.consume(owner) == true }
            if (!allowed) return AppReply.Failure("APPROVAL_REQUIRED", "安装需要用户批准或完全访问")
            val target = authorizedTarget(owner, command.pluginId, command.actionId)
                ?: return AppReply.Failure("PERMISSION_DENIED", "安装入口已停用")
            val result = target.execute(command.actionId, PluginContext("", "", inputs = mapOf("resourcePath" to relative,
                "_callerPlugin" to owner, "_agentApproved" to "true")))
            return data { put("completed", true); put("outcome", PluginJson.encodeToJsonElement(PluginOutcome.serializer(), result)) }
        }
        return withContext(Dispatchers.Main) {
            val model = ui() ?: return@withContext AppReply.Failure("HOST_UNAVAILABLE", "主界面已关闭")
            if (authorizedTarget(owner, command.pluginId, command.actionId) == null)
                return@withContext AppReply.Failure("PERMISSION_DENIED", "目标插件已停用")
            if (command.pluginId in model.state.value.busyPlugins)
                return@withContext AppReply.Failure("BUSY", "目标插件正在处理操作，资源尚未送交审核")
            model.invoke(command.pluginId, command.actionId, mapOf("resourcePath" to relative, "_callerPlugin" to owner))
            data { put("accepted", true); put("pending", true); put("completed", false); put("recipient", command.pluginId) }
        }
    }
    private fun applySetting(model: AppViewModel, command: AppCommand.Setting) {
        val theme = model.state.value.theme
        fun boolean() = command.value.toBooleanStrict()
        when (command.key) {
            "theme.mode" -> model.theme(theme.copy(mode = ThemeMode.valueOf(command.value)))
            "theme.color" -> { require(command.value.matches(Regex("#[a-fA-F0-9]{6}"))); model.theme(theme.copy(seed = 0xFF000000 or command.value.drop(1).toLong(16))) }
            "theme.wallpaper" -> model.theme(theme.copy(wallpaperEnabled = boolean()))
            "theme.contrast" -> model.theme(theme.copy(contrast = ContrastLevel.valueOf(command.value)))
            "theme.corners" -> model.theme(theme.copy(cornerLevel = CornerLevel.valueOf(command.value)))
            "theme.deeper-dark" -> model.theme(theme.copy(deeperDark = boolean()))
            "haptics" -> model.haptics(boolean())
            "subtitle.size-percent" -> { val percent = command.value.toFloat(); require(percent.isFinite()); model.setSubtitleSizePercent(percent) }
            "captions.size" -> { val size = command.value.toFloat(); require(size.isFinite()); model.setCaptionTextSize(size) }
            "subtitle.placeholder" -> model.setSubtitlePlaceholder(command.value)
            else -> throw IllegalArgumentException("没有开放此设置")
        }
    }
    private fun data(block: JsonObjectBuilder.() -> Unit) = AppReply.Data(buildJsonObject(block))
    companion object { val settingKeys = listOf("theme.mode", "theme.color", "theme.wallpaper", "theme.contrast", "theme.corners", "theme.deeper-dark", "haptics", "subtitle.size-percent", "captions.size", "subtitle.placeholder") }
}
