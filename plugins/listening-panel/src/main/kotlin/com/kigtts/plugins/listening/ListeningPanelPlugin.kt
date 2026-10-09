package com.kigtts.plugins.listening

import com.kigtts.plugin.api.*

/** Panel controls require a recognition service; no provider is called directly. */
class ListeningPanelPlugin : KigPlugin {
    override val descriptor = PluginDescriptor("panel.listening", "字幕插件", "通过语音识别服务驱动聆听字幕面板",
        requires = listOf(PluginRequirement.RECOGNITION_SERVICE))
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
        "mode" -> PluginOutcome.Menu("识别方式", listOf(PageAction("stream", "流式识别"), PageAction("batch", "完整录音识别（最多 8 秒）")))
        "stream" -> PluginOutcome.HostAction(HostCommand.USE_STREAMING)
        "batch" -> PluginOutcome.HostAction(HostCommand.USE_BATCH)
        else -> PluginOutcome.Message("未知聆听面板操作")
    }
}
