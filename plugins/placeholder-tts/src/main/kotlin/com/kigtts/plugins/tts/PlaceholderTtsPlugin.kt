package com.kigtts.plugins.tts

import com.kigtts.plugin.api.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

class PlaceholderTtsPlugin : KigPlugin {
    override val descriptor = PluginDescriptor("demo.tts", "朗读占位插件", "提供朗读占位接口", placeholder = true)
    override val tts = object : TtsEngine {
        override fun synthesize(request: TtsRequest) = flow {
            require(request.text.isNotBlank()) { "请先输入文本" }
            emit(SynthesisEvent.Started)
            delay(650)
            emit(SynthesisEvent.Finished("演示完成 · 未生成音频"))
        }
    }
    override val contributions = PluginContributions(
        drawerEntries = listOf(DrawerEntry("tts", "朗读插件", "tts", PluginIcon.SPEAKER)),
    )

    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome = when (actionId) {
        "about", "about-menu" -> PluginOutcome.Dialog("朗读占位插件", "此插件验证朗读接口和 UI 入口，不生成或播放声音。后续可以替换为社区 TTS 引擎。")
        "menu" -> PluginOutcome.Menu("朗读工具", listOf(PageAction("about", "插件说明"), PageAction("page", "打开朗读页面")))
        "page" -> PluginOutcome.OpenPage("tts")
        "save" -> PluginOutcome.Dialog("保存短语", "当前文本：${context.editorText.ifBlank { "（空）" }}\n\n这是插件对话框扩展示例；短语可通过主界面编辑按钮管理。")
        else -> PluginOutcome.Message("未知朗读操作")
    }

    override fun page(pageId: String, context: PluginContext) = if (pageId == "tts") PluginPage("tts", "朗读插件", listOf(
        PageBlock.Text("让文字拥有声音", heading = true),
        PageBlock.Notice("占位引擎 · 此版本不会产生音频"),
        PageBlock.Text("这是由朗读插件声明、宿主使用 MD3e 组件构建的页面。"),
        PageBlock.Group("当前文本", listOf(PageBlock.Text(context.editorText.ifBlank { "还没有输入文本" }))),
        PageBlock.Actions(listOf(PageAction("about", "查看插件说明", PluginIcon.INFO))),
    )) else null
}
