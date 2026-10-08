package com.kigtts.plugins.voice

import com.kigtts.plugin.api.*

/** Optional gesture owner; recording/recognition is intentionally still a placeholder. */
class VoiceInputPlugin(initiallyEnabled: Boolean = false, private val save: (Boolean) -> Unit = {}) : KigPlugin {
    private var enabled = initiallyEnabled
    override val descriptor = PluginDescriptor("input.voice", "语音输入插件", "可选的快捷文本长按入口，当前不录音", placeholder = true)
    override val contributions get() = PluginContributions(
        drawerEntries = listOf(DrawerEntry("settings", "语音输入", "settings", PluginIcon.MIC, settingsPage = true)),
        gestures = if (enabled) listOf(GestureAction("input", "语音输入", PluginGesture.QUICK_TEXT_LONG_PRESS)) else emptyList(),
    )
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome = when (actionId) {
        "toggle" -> { enabled = !enabled; save(enabled); PluginOutcome.RefreshUi }
        "input" -> if (enabled) PluginOutcome.Message("语音输入占位插件：暂未接入录音和识别") else PluginOutcome.RefreshUi
        else -> PluginOutcome.Message("未知语音输入操作")
    }
    override fun page(pageId: String, context: PluginContext) = if (pageId == "settings") PluginPage("settings", "语音输入", listOf(
        PageBlock.Text(if (enabled) "语音输入已开启" else "语音输入已关闭", heading = true),
        PageBlock.Text("开启后长按快捷文本按钮调用本插件；关闭后长按无行为。此占位插件不访问麦克风。"),
        PageBlock.Actions(listOf(PageAction("toggle", if (enabled) "关闭语音输入" else "开启语音输入", PluginIcon.MIC))),
    )) else null
}
