package com.kigtts.next.plugins.control

import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

private fun project(manager: PluginManager, owner: String, id: String): ManagedPackage? =
    manager.registry.plugin(owner)?.contributions?.managedPackages?.singleOrNull { it.id == id && it.kind == "lua" && it.enabled }

internal suspend fun managedPermission(manager: PluginManager, owner: String, capabilities: Set<String>, id: String, permission: String,
    authority: AgentAccessPolicy? = null): AppReply {
    val item = project(manager, owner, id) ?: return AppReply.Failure("PERMISSION_DENIED", "项目不存在或已停用")
    if (permission !in item.permissions || permission !in capabilities) return AppReply.Failure("PERMISSION_DENIED", "项目未声明此权限")
    val allowed = authority?.delegated(owner, id) == true || manager.permissions.request(manager.permissionOwner(owner, id), permission, item.name,
        item.permissionPurposes[permission] ?: "插件未提供具体用途；此权限允许${com.kigtts.plugin.runtime.PluginPermissions.label(permission)}。")
    return AppReply.Data(buildJsonObject { put("allowed", allowed) })
}

internal suspend fun managedCall(manager: PluginManager, model: AppViewModel?, owner: String, capabilities: Set<String>, request: AppCommand.ManagedCall): AppReply {
    val item = project(manager, owner, request.projectId) ?: return AppReply.Failure("PERMISSION_DENIED", "项目不存在或已停用")
    if (model == null) return AppReply.Failure("HOST_UNAVAILABLE", "请先打开KIGTTS")
    return try {
        val declared = item.permissions.intersect(capabilities)
        val quick = if (request.channel == "quick") PluginJson.decodeFromJsonElement<QuickTextCommand>(request.command) else null
        val stickers = if (request.channel == "stickers") PluginJson.decodeFromJsonElement<StickerCommand>(request.command) else null
        val app = if (request.channel == "app") PluginJson.decodeFromJsonElement<AppCommand>(request.command) else null
        val permission = when {
            quick != null -> quickTextPermission(quick, declared)
            stickers != null -> stickerPermission(stickers, declared)
            app != null -> "app-control"
            else -> return AppReply.Failure("INVALID", "不支持此项目通道")
        }
        val grant = managedPermission(manager, owner, capabilities, item.id, permission, model.agentAccess)
        if ((grant as? AppReply.Data)?.value?.get("allowed")?.jsonPrimitive?.booleanOrNull != true)
            return AppReply.Failure("PERMISSION_DENIED", "项目权限尚未授权或已禁用")
        val reply = when {
            quick != null -> PluginJson.encodeToJsonElement<QuickTextReply>(model.quickTextPlugins.forPlugin(owner, declared) { model.agentAccess.delegated(owner, item.id) }.request(quick))
            stickers != null -> PluginJson.encodeToJsonElement<StickerReply>(model.stickerPlugins.forPlugin(owner, declared) { model.agentAccess.delegated(owner, item.id) }.request(stickers))
            else -> {
                require(app is AppCommand.Describe || app is AppCommand.State || app is AppCommand.Navigate || app is AppCommand.SetText ||
                    app is AppCommand.SetAutomaticRead || app is AppCommand.Speak || app is AppCommand.StopSpeech || app is AppCommand.Setting) { "项目不能管理其它插件" }
                PluginJson.encodeToJsonElement<AppReply>(AppControlHost(manager) { model }.performManaged(owner, requireNotNull(app)))
            }
        }
        AppReply.Data(buildJsonObject { put("reply", reply) })
    } catch (cancel: CancellationException) { throw cancel }
    catch (failure: Exception) { AppReply.Failure("INVALID", failure.message?.take(300) ?: "项目操作失败") }
}
