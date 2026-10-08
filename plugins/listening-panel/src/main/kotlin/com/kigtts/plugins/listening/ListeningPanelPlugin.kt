package com.kigtts.plugins.listening

import com.kigtts.plugin.api.*

/** Panel controls operate through host commands, independently of the ASR engine. */
class ListeningPanelPlugin : KigPlugin {
    override val descriptor = PluginDescriptor("panel.listening", "字幕插件", "注册开始聆听入口与聆听字幕面板操作，调用宿主所选识别引擎")
    override val contributions get() = contributions(PluginContext("", ""))
    override fun contributions(context: PluginContext) = PluginContributions(actions = listOf(
        UiAction("toggle", if (context.listeningPanelVisible) "关闭聆听" else "开始聆听", UiSlot.EDITOR_TOP_START,
            PluginIcon.HEARING, buttonStyle = UiButtonStyle.ICON_TEXT),
        UiAction("mode", "识别方式", UiSlot.LISTENING_BOTTOM_START, PluginIcon.SETTINGS),
        UiAction("close", "收起聆听面板", UiSlot.LISTENING_BOTTOM_END, PluginIcon.CLOSE),
    ))
    override suspend fun execute(actionId: String, context: PluginContext) = when (actionId) {
        "toggle" -> PluginOutcome.HostAction(HostCommand.TOGGLE_LISTENING)
        "close" -> PluginOutcome.HostAction(HostCommand.CLOSE_LISTENING_PANEL)
        "mode" -> PluginOutcome.Menu("识别方式", listOf(PageAction("stream", "流式识别演示"), PageAction("batch", "非流式识别演示")))
        "stream" -> PluginOutcome.HostAction(HostCommand.USE_STREAMING)
        "batch" -> PluginOutcome.HostAction(HostCommand.USE_BATCH)
        else -> PluginOutcome.Message("未知聆听面板操作")
    }
}
