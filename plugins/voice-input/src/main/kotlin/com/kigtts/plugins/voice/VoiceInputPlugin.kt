package com.kigtts.plugins.voice

import com.kigtts.plugin.api.*

/** Optional gesture owner. The host routes hardware audio into the selected ASR plugin. */
class VoiceInputPlugin(initiallyEnabled: Boolean = false, private val save: (Boolean) -> Unit = {}) : KigPlugin {
    private var enabled = initiallyEnabled
    override val descriptor = PluginDescriptor("input.voice", "语音输入插件", "通过快捷文本长按入口调用所选语音识别插件")
    override val contributions get() = PluginContributions(
        drawerEntries = listOf(DrawerEntry("settings", "语音输入", "settings", PluginIcon.MIC, settingsPage = true)),
        gestures = if (enabled) listOf(GestureAction("input", "语音输入", PluginGesture.QUICK_TEXT_LONG_PRESS)) else emptyList(),
    )
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome = when (actionId) {
        "toggle" -> { enabled = !enabled; save(enabled); PluginOutcome.RefreshUi }
        "input" -> if (enabled) PluginOutcome.HostAction(HostCommand.VOICE_INPUT) else PluginOutcome.RefreshUi
        else -> PluginOutcome.Message("未知语音输入操作")
    }
    override fun page(pageId: String, context: PluginContext) = if (pageId == "settings") PluginPage("settings", "语音输入", listOf(
        PageBlock.Text(if (enabled) "语音输入已开启" else "语音输入已关闭", heading = true),
        PageBlock.Text("开启后长按快捷文本按钮录音最多 5 秒，通过所选识别插件更新大字幕；首次使用请求麦克风权限。关闭后长按无行为。"),
        PageBlock.Actions(listOf(PageAction("toggle", if (enabled) "关闭语音输入" else "开启语音输入", PluginIcon.MIC))),
    )) else null
}
