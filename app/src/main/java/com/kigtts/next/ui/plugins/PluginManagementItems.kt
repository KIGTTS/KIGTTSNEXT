package com.kigtts.next.ui.plugins

import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*

internal fun pluginManagementItems(model: AppViewModel): List<ManagementItem> {
    val external = model.pluginManager.status.associateBy { it.installed.manifest.id }
    val managed = model.registry.plugins.flatMap { runtime -> runtime.contributions.managedPackages.map { "${runtime.descriptor.id}::${it.id}" to (runtime.descriptor.id to it) } }.toMap()
    val all = (external.keys + model.registry.plugins.map { it.descriptor.id } + managed.keys).distinct()
        .filterNot { it == "service.speech-synthesis" }
    return model.pluginOrder(all).map { id ->
        managed[id]?.let { (runtime, item) ->
            val primary = item.manageAction?.let { PageAction("managed/$runtime/$it", "设置", PluginIcon.SETTINGS) }
            val actions = listOfNotNull(
                PageAction("about/$id", "关于", PluginIcon.INFO),
                PageAction("permissions/$runtime/${item.id}", "权限", PluginIcon.SETTINGS, symbol = "shield_locked"),
                item.removeAction?.let { PageAction("managed-remove/$runtime/${item.id}", "卸载", PluginIcon.CLOSE) })
            return@map ManagementItem(id, item.name, "v${item.version} · Lua" + if (item.error.isNotBlank()) " · ${item.error}" else if (!item.enabled) " · 已停用" else "",
                icon = PluginIcon.TEXT, primaryAction = primary?.id, actions = actions,
                toggle = item.enableAction?.let { ManagementToggle(item.enabled, "managed/$runtime/$it") })
        }
        val installed = external[id]; val plugin = model.registry.plugin(id)
        val name = installed?.installed?.manifest?.name ?: plugin!!.descriptor.name
        val version = installed?.installed?.manifest?.version ?: plugin!!.descriptor.version
        val actions = mutableListOf<PageAction>()
        val contribution = plugin?.contributions(model.context())
        val entry = contribution?.settingsEntries?.firstOrNull()
        val page = entry?.pageId ?: contribution?.drawerEntries?.firstOrNull()?.pageId
        actions += PageAction("about/$id", "关于", PluginIcon.INFO)
        if (installed != null) actions += PageAction("permissions/$id", "权限", PluginIcon.SETTINGS, symbol = "shield_locked")
        if (installed != null) actions += PageAction("remove/$id", "卸载", PluginIcon.CLOSE)
        val availability = if (plugin?.tts?.info?.availability == EngineAvailability.NEEDS_RESOURCES || plugin?.asr?.info?.availability == EngineAvailability.NEEDS_RESOURCES) " · 需要先导入资源" else ""
        val dependency = if (plugin != null && !model.registry.available(plugin)) " · 缺少依赖：语音识别服务" else ""
        ManagementItem(id, name, "v$version · " + (installed?.error ?: if (installed?.installed?.enabled == false) "已停用" else plugin?.descriptor?.description.orEmpty()) + availability + dependency,
            icon = if (plugin?.asr != null) PluginIcon.HEARING else if (plugin?.tts != null) PluginIcon.SPEAKER else PluginIcon.SETTINGS,
            primaryAction = page?.let { "page/$id/$it" }, actions = actions,
            toggle = installed?.let { ManagementToggle(it.installed.enabled, "enable/$id") })
    }
}
