package com.kigtts.plugins.asr

import com.kigtts.plugin.api.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PlaceholderAsrPlugin : KigPlugin {
    override val descriptor = PluginDescriptor("demo.asr", "聆听占位插件", "演示流式与非流式识别", placeholder = true)
    override val asr = object : AsrEngine {
        override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig): RecognitionResult {
            delay(500)
            return RecognitionResult("这是非流式识别的演示字幕。", placeholder = true)
        }
        override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig) = flow {
            emit(RecognitionEvent.Started)
            val words = listOf("你好，", "你好，这是", "你好，这是流式识别", "你好，这是流式识别的演示字幕。")
            var count = 0
            audio.collect {
                emit(RecognitionEvent.Partial(words[count.coerceAtMost(words.lastIndex)]))
                count++
            }
            emit(RecognitionEvent.Final(RecognitionResult(if (count == 0) "" else words[(count - 1).coerceAtMost(words.lastIndex)], placeholder = true)))
        }
    }
    override val contributions = PluginContributions(
        drawerEntries = listOf(DrawerEntry("asr", "聆听插件", "asr", PluginIcon.MIC)),
    )
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome = when (actionId) {
        "about", "about-menu", "mode" -> PluginOutcome.Dialog("聆听占位插件", "支持 recognize（完整 PCM → 单次结果）与 recognizeStream（PCM 流 → 中间结果 / 最终结果）。主界面菜单可以选择演示方式，不读取麦克风。")
        "page" -> PluginOutcome.OpenPage("asr")
        "example" -> PluginOutcome.ReplaceEditorText("你好，很高兴见到你。")
        else -> PluginOutcome.Message("未知聆听操作")
    }
    override fun page(pageId: String, context: PluginContext) = if (pageId == "asr") PluginPage("asr", "聆听插件", listOf(
        PageBlock.Text("把声音变成文字", heading = true),
        PageBlock.Notice("占位引擎 · 字幕来自固定演示文本"),
        PageBlock.Group("流式识别", listOf(PageBlock.Text("逐个接收 PCM 数据块，发布 Partial，中途取消会结束会话；输入结束发布 Final。"))),
        PageBlock.Group("非流式识别", listOf(PageBlock.Text("传入完整的 PCM 录音，异步返回一个识别结果。"))),
        PageBlock.Text("最近字幕：${context.transcript.ifBlank { "还没有开始聆听" }}"),
        PageBlock.Actions(listOf(PageAction("example", "将示例写入编辑器", PluginIcon.TEXT), PageAction("about", "插件说明"))),
    )) else null
}
