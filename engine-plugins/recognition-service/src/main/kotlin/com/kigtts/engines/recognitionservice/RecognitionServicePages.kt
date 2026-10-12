package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.*

internal fun recognitionServicePage(id: String, settings: RecognitionServiceSettings, broker: RecognitionBroker): PluginPage? {
    val all = settings.ordered(broker.providers()); val selected = settings.current(all)
    if (id == "manager") return PluginPage(id, "语音识别服务", listOf(
        PageBlock.Actions(listOf(PageAction("activate", "用于语音识别", PluginIcon.MIC)))
    ), management = ManagementList("recognition-engines", all.map { provider ->
        ManagementItem(provider.pluginId, provider.name,
            (if (!provider.enabled) "已停用" else if (provider.info.availability == EngineAvailability.READY) recognitionKind(provider.info.streaming) else "请添加识别包"),
            PluginIcon.MIC, selected = provider.pluginId == selected?.pluginId, primaryAction = "engine/${provider.pluginId}",
            actions = listOf(PageAction("select/${provider.pluginId}", "选用此引擎", PluginIcon.MIC)),
            toggle = ManagementToggle(provider.enabled, "toggle/${provider.pluginId}"))
    }, "reorder", "请安装独立语音识别引擎插件"))
    if (id.startsWith("engine/")) {
        val provider = all.firstOrNull { it.pluginId == id.substringAfter('/') } ?: return null
        val items = listOf(
            ManagementItem("enabled", "启用", icon = PluginIcon.MIC, group = "识别",
                toggle = ManagementToggle(provider.enabled, "toggle/${provider.pluginId}")),
            ManagementItem("select", "用于识别", recognitionKind(provider.info.streaming), PluginIcon.MIC, group = "识别",
                selected = provider.pluginId == selected?.pluginId, primaryAction = "select/${provider.pluginId}")) +
            if (provider.enabled) provider.settingsPages.map { page ->
                ManagementItem("settings/${page.id}", page.title, icon = page.icon, group = "设置",
                    primaryAction = "settings/${provider.pluginId}/${page.id}")
            } else emptyList()
        return PluginPage(id, provider.name, emptyList(), ManagementList("recognition-settings/${provider.pluginId}", items))
    }
    return null
}
internal fun recognitionKind(kind: StreamingKind) = when (kind) {
    StreamingKind.INCREMENTAL -> "边说边识别"
    StreamingKind.SEGMENTED -> "分段识别"
    StreamingKind.NONE -> "录音识别"
}
