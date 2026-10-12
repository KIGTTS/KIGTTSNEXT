package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.*

class RecognitionServicePlugin : KigPlugin {
    override val descriptor = PluginDescriptor("service.speech-recognition", "语音识别服务", "统一管理独立识别引擎，供字幕与语音输入调用", version = "0.6.0")
    private lateinit var settings: RecognitionServiceSettings
    private lateinit var broker: RecognitionBroker
    private lateinit var facade: ServiceRecognition
    override val asr: AsrEngine get() = facade
    override suspend fun attach(environment: PluginEnvironment) {
        settings = RecognitionServiceSettings(environment.storage)
        broker = requireNotNull(environment.recognitionBroker) { "宿主未授予语音识别服务能力" }
        facade = ServiceRecognition(settings, broker)
    }
    override val contributions = PluginContributions(drawerEntries = listOf(DrawerEntry("manager", "语音识别服务", "manager", PluginIcon.MIC)))
    override fun page(pageId: String, context: PluginContext) = recognitionServicePage(pageId, settings, broker)
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
        val parts = actionId.split('/'); val id = parts.getOrNull(1)
        fun provider() = broker.providers().first { it.pluginId == id }
        return when (parts[0]) {
            "activate" -> PluginOutcome.SelectRecognitionService(descriptor.id)
            "engine" -> PluginOutcome.OpenPage(actionId)
            "select" -> {
                val selected = provider(); settings.provider = selected.pluginId; settings.save()
                if (!selected.enabled) PluginOutcome.SetPluginEnabled(selected.pluginId, true) else PluginOutcome.RecognitionRoutingChanged
            }
            "toggle" -> provider().let { PluginOutcome.SetPluginEnabled(it.pluginId, !it.enabled) }
            "settings" -> {
                val selected = provider(); check(selected.enabled) { "请先启用引擎" }
                val page = parts.drop(2).joinToString("/"); require(selected.settingsPages.any { it.id == page })
                PluginOutcome.OpenPluginPage(selected.pluginId, page)
            }
            "reorder" -> { settings.reorder(PluginJson.decodeFromString(requireNotNull(context.inputs["order"])), broker.providers()); PluginOutcome.RefreshUi }
            else -> PluginOutcome.Message("未知识别服务操作")
        }
    }
}
