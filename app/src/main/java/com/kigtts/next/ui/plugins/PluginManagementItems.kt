package com.kigtts.next.ui.plugins

import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*

internal fun pluginManagementItems(model: AppViewModel): List<ManagementItem> {
    val external = model.pluginManager.status.associateBy { it.installed.manifest.id }
    val managed = model.registry.plugins.flatMap { runtime -> runtime.contributions.managedPackages.map { "${runtime.descriptor.id}::${it.id}" to (runtime.descriptor.id to it) } }.toMap()
    val all = (external.keys + model.registry.plugins.map { it.descriptor.id } + managed.keys).distinct()
    return model.pluginOrder(all).map { id ->
        managed[id]?.let { (runtime, item) ->
            val actions = listOfNotNull(item.manageAction?.let { PageAction("managed/$runtime/$it", "编辑与调试", PluginIcon.EDIT) },
                PageAction("permissions/$runtime/${item.id}", "权限", PluginIcon.SETTINGS, symbol = "shield_locked"),
                item.removeAction?.let { PageAction("managed-remove/$runtime/${item.id}", "卸载并删除插件数据", PluginIcon.CLOSE) })
            return@map ManagementItem(id, item.name, "v${item.version} · Lua" + if (item.error.isNotBlank()) " · ${item.error}" else if (!item.enabled) " · 已停用" else "",
                icon = PluginIcon.TEXT, primaryAction = actions.firstOrNull()?.id, actions = actions,
                toggle = item.enableAction?.let { ManagementToggle(item.enabled, "managed/$runtime/$it") })
        }
        val installed = external[id]; val plugin = model.registry.plugin(id)
        val name = installed?.installed?.manifest?.name ?: plugin!!.descriptor.name
        val version = installed?.installed?.manifest?.version ?: plugin!!.descriptor.version
        val actions = mutableListOf<PageAction>()
        if (installed != null) actions += PageAction("permissions/$id", "权限", PluginIcon.SETTINGS, symbol = "shield_locked")
        val contribution = plugin?.contributions(model.context())
        contribution?.settingsEntries?.forEach { entry -> actions += PageAction("page/$id/${entry.pageId}", "打开${entry.label}", entry.icon) }
        contribution?.drawerEntries?.filterNot { entry -> contribution.settingsEntries.any { it.pageId == entry.pageId } }
            ?.forEach { entry -> actions += PageAction("page/$id/${entry.pageId}", "打开${entry.label}", entry.icon) }
        plugin?.tts?.let { engine ->
            if (engine.info.role == TtsRole.SERVICE || model.registry.plugins.none { it.tts?.info?.role == TtsRole.SERVICE })
                actions += PageAction("tts/$id", if (model.selectedTtsId == id) "当前语音合成引擎" else "用作语音合成引擎", PluginIcon.SPEAKER)
        }
        plugin?.asr?.takeIf { it.info.role == AsrRole.SERVICE }?.let {
            actions += PageAction("asr/$id", if (model.selectedAsrId == id) "当前语音识别服务" else "用作语音识别服务", PluginIcon.HEARING)
        }
        if (installed != null) actions += PageAction("remove/$id", "卸载并删除插件数据", PluginIcon.CLOSE)
        val availability = if (plugin?.tts?.info?.availability == EngineAvailability.NEEDS_RESOURCES || plugin?.asr?.info?.availability == EngineAvailability.NEEDS_RESOURCES) " · 需要先导入资源" else ""
        val dependency = if (plugin != null && !model.registry.available(plugin)) " · 缺少依赖：语音识别服务" else ""
        ManagementItem(id, name, "v$version · " + (installed?.error ?: if (installed?.installed?.enabled == false) "已停用" else plugin?.descriptor?.description.orEmpty()) + availability + dependency,
            icon = if (plugin?.asr != null) PluginIcon.HEARING else if (plugin?.tts != null) PluginIcon.SPEAKER else PluginIcon.SETTINGS,
            primaryAction = actions.firstOrNull { it.id.startsWith("page/") }?.id, actions = actions,
            toggle = installed?.let { ManagementToggle(it.installed.enabled, "enable/$id") })
    }
}
