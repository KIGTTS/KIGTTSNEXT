package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.*

internal fun recognitionServicePage(id: String, settings: RecognitionServiceSettings, broker: RecognitionBroker): PluginPage? {
    val all = settings.ordered(broker.providers()); val selected = settings.current(all)
    if (id == "manager") return PluginPage(id, "语音识别服务", listOf(
        PageBlock.Notice("当前引擎：${selected?.name ?: "未选用可用引擎"}"),
        PageBlock.Text("字幕和语音输入统一通过此服务调用独立识别引擎。模型及语言包仍由各引擎管理。"),
        PageBlock.Actions(listOf(PageAction("activate", "设为应用识别服务", PluginIcon.MIC)))
    ), management = ManagementList("recognition-engines", all.map { provider ->
        ManagementItem(provider.pluginId, provider.name,
            (if (!provider.enabled) "已停用" else if (provider.info.availability == EngineAvailability.READY) "已就绪" else "需要配置模型") + " · " + recognitionKind(provider.info.streaming),
            PluginIcon.MIC, selected = provider.pluginId == selected?.pluginId, primaryAction = "engine/${provider.pluginId}",
            actions = listOf(PageAction("select/${provider.pluginId}", "选用此引擎", PluginIcon.MIC)),
            toggle = ManagementToggle(provider.enabled, "toggle/${provider.pluginId}"))
    }, "reorder", "请安装独立语音识别引擎插件"))
    if (id.startsWith("engine/")) {
        val provider = all.firstOrNull { it.pluginId == id.substringAfter('/') } ?: return null
        val actions = mutableListOf(PageAction("select/${provider.pluginId}", "选用此引擎", PluginIcon.MIC),
            PageAction("toggle/${provider.pluginId}", if (provider.enabled) "停用引擎" else "启用引擎", PluginIcon.SETTINGS))
        if (provider.enabled) provider.settingsPages.forEach { page -> actions += PageAction("settings/${provider.pluginId}/${page.id}", page.title, page.icon) }
        return PluginPage(id, provider.name, listOf(PageBlock.Text(provider.name, true),
            PageBlock.Text("${recognitionKind(provider.info.streaming)}\n${if (provider.info.batch) "支持完整录音" else "不支持完整录音"} · ${provider.info.inputFormat.sampleRate} Hz"),
            PageBlock.Notice(if (provider.enabled) "识别包与语言设置在下方的引擎页面管理。" else "启用后可以管理识别包与设置。"), PageBlock.Actions(actions)))
    }
    return null
}
internal fun recognitionKind(kind: StreamingKind) = when (kind) {
    StreamingKind.INCREMENTAL -> "增量流式"
    StreamingKind.SEGMENTED -> "分段离线"
    StreamingKind.NONE -> "完整录音"
}
