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
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w480dp-h1000dp-xhdpi")
class ChatExecutionStepsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun liveStepsHaveTheSameLoadingStyleAndMergeOnlyAfterFinishing() {
        val thought = ChatMessage("plan", ChatRole.ASSISTANT, "", reasoning = "先检查图片资源", hasReasoning = true)
        val tool = ChatMessage("read", ChatRole.TOOL, "已读取", toolName = "读取文件")
        val liveTool = ChatMessage("download", ChatRole.TOOL, "", toolName = "下载图片", state = ChatMessageState.STREAMING)
        val liveThought = ChatMessage("next", ChatRole.ASSISTANT, "", thinking = true, state = ChatMessageState.STREAMING)
        var messages by mutableStateOf(listOf(thought, tool, liveTool, liveThought))
        compose.setContent { KigttsTheme(ThemeSettings()) { Column(Modifier.width(420.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chatTimeline(messages).forEach { row -> key(row.key) {
                if (row.steps.isNotEmpty()) ChatExecutionGroup(row.steps)
                else row.answer?.let { ChatBubble(it, showThought = false) }
            } }
        } } }
        compose.onAllNodesWithTag("chat-step-loading").assertCountEquals(2)
        compose.onNodeWithTag("chat-tools-toggle/plan").performClick()
        compose.onNodeWithTag("chat-thought/plan").assertIsDisplayed()
        compose.onNodeWithTag("chat-tool/read").assertIsDisplayed()
        val toolHeight = compose.onNodeWithTag("chat-tool/download").fetchSemanticsNode().boundsInRoot.height
        val thoughtHeight = compose.onNodeWithTag("chat-thought/next").fetchSemanticsNode().boundsInRoot.height
        assertEquals(toolHeight, thoughtHeight, .1f)
        save("chat-execution-active.png")
        compose.runOnIdle { messages = listOf(thought, tool, liveTool.copy(state = ChatMessageState.COMPLETE), liveThought) }
        compose.onAllNodesWithTag("chat-step-loading").assertCountEquals(1)
        compose.onNodeWithText("思考与工具 · 3 步").assertIsDisplayed()
        compose.onNodeWithTag("chat-tool/download").assertIsDisplayed()
        compose.runOnIdle { messages = messages.dropLast(1) + liveThought.copy(thinking = false, hasReasoning = true,
            reasoning = "检查完成", text = "已完成资源处理", state = ChatMessageState.COMPLETE) }
        compose.onAllNodesWithTag("chat-step-loading").assertCountEquals(0)
        compose.onNodeWithText("思考与工具 · 4 步").assertIsDisplayed()
        compose.onNodeWithTag("chat-answer/next").assertExists()
        compose.onNodeWithText("回答").assertExists()
        save("chat-execution-complete.png")
    }
    private fun save(name: String) {
        val file = File("build/previews/$name").apply { requireNotNull(parentFile).mkdirs() }
        compose.onRoot().captureRoboImage(file.absolutePath)
    }
}
