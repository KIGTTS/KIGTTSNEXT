package com.kigtts.next.ui.plugins

import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*

internal fun pluginManagementItems(model: AppViewModel): List<ManagementItem> {
    val external = model.pluginManager.status.associateBy { it.installed.manifest.id }
    val all = (external.keys + model.registry.plugins.map { it.descriptor.id }).distinct()
    return model.pluginOrder(all).map { id ->
        val installed = external[id]; val plugin = model.registry.plugin(id)
        val name = installed?.installed?.manifest?.name ?: plugin!!.descriptor.name
        val version = installed?.installed?.manifest?.version ?: plugin!!.descriptor.version
        val actions = mutableListOf<PageAction>()
        plugin?.contributions?.drawerEntries?.forEach { entry -> actions += PageAction("page/$id/${entry.pageId}", "打开${entry.label}", entry.icon) }
        plugin?.tts?.let { engine ->
            if (engine.info.role == TtsRole.SERVICE || model.registry.plugins.none { it.tts?.info?.role == TtsRole.SERVICE })
                actions += PageAction("tts/$id", if (model.selectedTtsId == id) "当前语音合成引擎" else "用作语音合成引擎", PluginIcon.SPEAKER)
        }
        plugin?.asr?.let { actions += PageAction("asr/$id", if (model.selectedAsrId == id) "当前语音识别引擎" else "用作语音识别引擎", PluginIcon.HEARING) }
        if (installed != null) actions += PageAction("remove/$id", "卸载并删除插件数据", PluginIcon.CLOSE)
        val availability = if (plugin?.tts?.info?.availability == EngineAvailability.NEEDS_RESOURCES || plugin?.asr?.info?.availability == EngineAvailability.NEEDS_RESOURCES) " · 需要先导入资源" else ""
        ManagementItem(id, name, "v$version · " + (installed?.error ?: if (installed?.installed?.enabled == false) "已停用" else plugin?.descriptor?.description.orEmpty()) + availability,
            icon = if (plugin?.asr != null) PluginIcon.HEARING else if (plugin?.tts != null) PluginIcon.SPEAKER else PluginIcon.SETTINGS,
            primaryAction = actions.firstOrNull { it.id.startsWith("page/") }?.id, actions = actions,
            toggle = installed?.let { ManagementToggle(it.installed.enabled, "enable/$id") })
    }
}
