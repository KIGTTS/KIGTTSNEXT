package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.*
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w480dp-h800dp-xhdpi")
class ChatExpansionStreamingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun expandingToolsKeepsTheHeaderStillWhileNewReplyChunksArrive() {
        var ending by mutableStateOf("正在生成")
        val history = (0..12).map { ChatMessage("old$it", ChatRole.ASSISTANT, "历史消息 $it") }
        val tools = listOf(ChatMessage("tool", ChatRole.TOOL, "完成", toolName = "下载图片"),
            ChatMessage("tool-two", ChatRole.TOOL, "完成", toolName = "读取网页"))
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.size(450.dp, 720.dp)) {
            PluginChatScreen(PageBlock.Chat(history + tools + ChatMessage("reply", ChatRole.ASSISTANT, ending,
                state = ChatMessageState.STREAMING), "send", "stop", running = true, conversationId = "expansion"), true, { _, _ -> }) {}
        } } }
        compose.onNodeWithTag("chat-tools-toggle/tool").performClick()
        compose.waitForIdle()
        fun top() = compose.onNodeWithTag("chat-tools-toggle/tool").fetchSemanticsNode().boundsInRoot.top
        val expected = top()
        repeat(8) { index ->
            compose.runOnIdle { ending += "\n流式追加段落 " + index }
            compose.waitForIdle()
            assertEquals("Streaming must not fight the expanded reading position", expected, top(), 1f)
        }
        compose.onNodeWithText("下载图片").assertIsDisplayed()
        compose.onNodeWithTag("plugin-chat-bottom").assertIsDisplayed().performClick()
        compose.onNodeWithText("流式追加段落 7", substring = true).assertIsDisplayed()
    }
    @Test fun completedThoughtAndToolsUseTheSameCompactTouchableHeight() {
        compose.setContent { KigttsTheme(ThemeSettings()) { Column(Modifier.width(450.dp)) {
            ChatBubble(ChatMessage("thought", ChatRole.ASSISTANT, "", hasReasoning = true, summary = "摘要"))
            ChatToolGroup(listOf(ChatMessage("tool", ChatRole.TOOL, "完成", toolName = "工具")))
        } } }
        val thought = compose.onNodeWithTag("chat-message/thought").fetchSemanticsNode().boundsInRoot.height
        val tools = compose.onNodeWithTag("chat-tools/tool").fetchSemanticsNode().boundsInRoot.height
        assertEquals("Execution headers share a compact row", thought, tools, 1f)
        assertTrue("Keep a usable 48dp outer target", thought >= 96f)
    }
}
