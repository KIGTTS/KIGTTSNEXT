package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.PluginChatScreen
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w480dp-h800dp-xhdpi")
class AgentChatUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun userScrollOwnsTheViewportWhileStreamingUntilReturnToBottom() {
        var messages by mutableStateOf((0..45).map { ChatMessage("m$it", ChatRole.ASSISTANT, "第 $it 条消息，保留阅读位置。") })
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.size(450.dp, 720.dp)) {
            PluginChatScreen(PageBlock.Chat(messages, "send", "stop", running = true, conversationId = "one"), true, { _, _ -> }) {}
        } } }
        compose.onNodeWithTag("plugin-chat-history").performTouchInput { swipeDown() }
        compose.waitForIdle()
        fun position() = compose.onNodeWithTag("plugin-chat-history").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before = position()
        repeat(5) { index -> compose.runOnIdle { messages = messages.dropLast(1) + messages.last().copy(text = "流式更新 $index " + "回答内容".repeat(index + 1)) }; compose.waitForIdle() }
        assertEquals("Incoming chunks must not drag the reader to the bottom", before, position(), .05f)
        compose.onNodeWithTag("plugin-chat-bottom").assertIsDisplayed().performClick()
        compose.onNodeWithText("流式更新 4 " + "回答内容".repeat(5)).assertIsDisplayed()
    }
    @Test fun approvalAndSelectorsRemainAboveComposerAndToolCallsExpandAsOneGroup() {
        val invoked = mutableListOf<Pair<String, Map<String, String>>>()
        val chat = PageBlock.Chat(listOf(ChatMessage("answer", ChatRole.ASSISTANT, "**粗体** 与 [来源](https://example.com)", summary = "思考摘要：准备创建计算器。"),
            ChatMessage("tool1", ChatRole.TOOL, "已完成", toolName = "已读取文件", detail = "index.html", icon = PluginIcon.TEXT),
            ChatMessage("tool2", ChatRole.TOOL, "已完成", toolName = "创建网页卡片", icon = PluginIcon.ADD)), "send", "stop", conversationId = "two",
            controls = listOf(ChatControl("权限", listOf(ChatOption("ask", "请求批准", "ask"), ChatOption("full", "完全访问", "full")), "ask", ChatControlKind.ACCESS)),
            approval = ChatApproval("请求批准", "approve", "reject", "创建网页快捷文本"))
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.size(450.dp, 720.dp)) { PluginChatScreen(chat, true, { action, inputs -> invoked += action to inputs }) {} } } }
        val approval = compose.onNodeWithTag("plugin-chat-approval").fetchSemanticsNode().boundsInRoot
        val input = compose.onNodeWithTag("plugin-chat-input").fetchSemanticsNode().boundsInRoot
        val history = compose.onNodeWithTag("plugin-chat-history").fetchSemanticsNode().boundsInRoot
        assertTrue(approval.top >= history.bottom - 1f); assertTrue(approval.bottom <= input.top)
        compose.onNodeWithText("工具调用 · 2 项").performClick()
        compose.onNodeWithText("已读取文件").assertIsDisplayed()
        compose.onNodeWithText("批准并继续").performClick()
        assertEquals("true", invoked.last().second["_agentApprove"])
        compose.onNodeWithTag("plugin-chat-control/ACCESS").performClick(); compose.onNodeWithText("完全访问").performClick()
        assertEquals("full", invoked.last().second["_agentAccess"])
        java.io.File("build/previews").mkdirs()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/previews/agent-composer-approval.png")
    }
}
