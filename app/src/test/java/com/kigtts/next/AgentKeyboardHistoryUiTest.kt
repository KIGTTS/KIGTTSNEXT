@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w480dp-h900dp-xhdpi")
class AgentKeyboardHistoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun imeHeightFramesMoveComposerWithoutRemeasuringMarkdownHistory() {
        var height by mutableStateOf(720.dp); var measures = 0
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.width(450.dp).layout { measurable, constraints ->
            val targetHeight = height.roundToPx(); val child = measurable.measure(constraints.copy(minHeight = 0, maxHeight = targetHeight))
            layout(child.width, targetHeight) { child.place(0, 0) }
        }) {
            ChatViewport(Modifier.fillMaxSize(), history = {
                Column(Modifier.fillMaxSize().layout { measurable, constraints -> measures++; val child = measurable.measure(constraints); layout(child.width, child.height) { child.place(0, 0) } }) {
                    repeat(10) { ChatRichText("| 名称 | 结果 |\n|---|---|\n| 原生控件 | **保持稳定** |") }
                }
            }, composer = { Box(Modifier.fillMaxWidth().height(60.dp).testTag("composer")) { Text("输入栏") } })
        } } }
        compose.waitForIdle(); val before = measures; val original = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot.top
        (1..16).forEach { frame -> compose.runOnIdle { height = (720 - frame * 16).dp }; compose.waitForIdle() }
        val after = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot.top
        assertTrue(after < original); assertEquals("IME frames must reuse fixed history measurement", before, measures)
        (1..16).forEach { frame -> compose.runOnIdle { height = (464 + frame * 16).dp }; compose.waitForIdle() }
        assertEquals("Closing IME must reuse history too", before, measures)
        assertEquals(original, compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot.top, .1f)
    }
    @Test fun runningInputCanQueueSteerAndStopWithoutClearingTheDraftByAccident() {
        val actions = mutableListOf<Pair<String, Map<String, String>>>()
        val chat = PageBlock.Chat(emptyList(), "send", "stop", running = true, queueAction = "queue", steerAction = "legacy-steer",
            queued = listOf(ChatQueuedMessage("q", "优先消息", "remove", "front", "steer", "order")))
        compose.setContent { KigttsTheme(ThemeSettings()) { PluginChatScreen(chat, true, { action, args -> actions += action to args }) {} } }
        compose.onNodeWithTag("plugin-chat-input").performTextInput("排队消息")
        compose.onNodeWithTag("plugin-chat-send").performClick(); assertEquals("queue", actions.last().first); assertEquals("排队消息", actions.last().second["message"])
        compose.onNodeWithTag("plugin-chat-input").performTextInput("优先消息")
        compose.onNodeWithTag("plugin-chat-steer").assertDoesNotExist()
        compose.onNodeWithTag("chat-queue-steer/q").performClick(); assertEquals("steer", actions.last().first)
        compose.onNodeWithTag("plugin-chat-input").assertTextContains("优先消息")
        compose.onNodeWithTag("plugin-chat-stop").assertDoesNotExist()
        compose.onNodeWithTag("plugin-chat-input").performTextClearance()
        compose.onNodeWithTag("plugin-chat-stop").performClick(); assertEquals("stop", actions.last().first)
    }
    @Test fun queuedMessagesReorderWithTheLeftGripAndSubmitOnlyOnRelease() {
        val actions = mutableListOf<Pair<String, Map<String, String>>>()
        val chat = PageBlock.Chat(emptyList(), "send", "stop", running = true,
            queued = (1..3).map { ChatQueuedMessage("q$it", "排队 $it", "remove/$it", "front/$it", "steer/$it", "reorder") })
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.size(450.dp, 720.dp)) {
            PluginChatScreen(chat, true, { action, args -> actions += action to args }) {}
        } } }
        compose.onNodeWithTag("chat-queue-grip/q3").performTouchInput {
            down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, -192f), delayMillis = 300)
        }
        assertTrue(actions.isEmpty())
        compose.onNodeWithTag("chat-queue-grip/q3").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, actions.count { it.first == "reorder" })
        assertEquals(listOf("q3", "q1", "q2"), PluginJson.decodeFromString<List<String>>(actions.single().second.getValue("order")))
    }
    @Test fun cachedHistoryCannotDrawBehindComposerAfterKeyboardShrinksViewport() {
        var height by mutableStateOf(720.dp)
        compose.setContent { Box(Modifier.width(450.dp).height(height).background(Color.Blue).testTag("chat-clip-fixture")) {
            ChatViewport(Modifier.fillMaxSize(), history = {
                Box(Modifier.fillMaxSize().background(Color.Red))
            }, composer = { Box(Modifier.fillMaxWidth().height(80.dp)) })
        } }
        compose.waitForIdle()
        compose.runOnIdle { height = 440.dp }
        compose.waitForIdle()
        fun assertComposerClear() {
            java.io.File("build/previews").mkdirs()
            val path = "build/previews/chat-history-clip-$height.png"
            compose.onNodeWithTag("chat-clip-fixture").captureRoboImage(path)
            val pixels = requireNotNull(android.graphics.BitmapFactory.decodeFile(path))
            try {
                assertEquals(android.graphics.Color.BLUE, pixels.getPixel(pixels.width / 2, pixels.height - 20))
                assertEquals(android.graphics.Color.RED, pixels.getPixel(pixels.width / 2, 20))
            } finally { pixels.recycle() }
        }
        assertComposerClear()
        compose.runOnIdle { height = 600.dp }; compose.waitForIdle()
        assertComposerClear()
    }
    @Test fun earlierMessagesMergeAndPullUsesContainedIndicator() {
        var chat by mutableStateOf(PageBlock.Chat((100..199).map { ChatMessage("m$it", ChatRole.USER, "消息$it", position = it) }, "send", "stop", conversationId = "history", historyAction = "earlier", historyTotal = 200))
        val actions = mutableListOf<Int>(); val cache = ChatHistoryCache(); var enabled by mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.size(450.dp, 740.dp)) { PluginChatScreen(chat, enabled, { action, args -> if (action == "earlier") { actions += args.getValue("before").toInt(); enabled = false } }, historyCache = cache) {} } } }
        compose.onNodeWithTag("plugin-chat-history").performScrollToIndex(0)
        compose.onNodeWithText("加载更早").performClick()
        assertEquals(listOf(100), actions)
        compose.runOnIdle { enabled = true; chat = chat.copy(messages = (50..99).map { ChatMessage("m$it", ChatRole.USER, "消息$it", position = it) } + chat.messages.takeLast(1)) }
        compose.waitForIdle()
        assertEquals(150, cache.merge("history", chat).size)
        compose.onNodeWithTag("plugin-chat-history").performScrollToIndex(0)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("chat-history-loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("plugin-chat-history").performTouchInput { swipeDown(startY = 100f, endY = 850f, durationMillis = 450) }
        assertEquals(50, actions.last())
        compose.onNodeWithTag("chat-history-loading").assertExists()
    }
    @Test fun thoughtAndToolDetailsRemainCollapsedUntilExplicitExpansion() {
        val message = ChatMessage("thinking", ChatRole.ASSISTANT, "", reasoning = "公开思考详情", state = ChatMessageState.STREAMING, thinking = true)
        compose.setContent { KigttsTheme(ThemeSettings()) { Column {
            ChatBubble(message)
            ChatToolGroup(listOf(ChatMessage("tool", ChatRole.TOOL, "独立结果", detail = "调用参数", toolName = "读取文件")))
        } } }
        compose.onNodeWithText("思考中").assertIsDisplayed(); compose.onNodeWithText("公开思考详情").assertDoesNotExist()
        compose.onNodeWithText("工具调用 · 1 项").performClick(); compose.onNodeWithText("独立结果").assertDoesNotExist()
        compose.onNodeWithTag("chat-tool/tool").performClick(); compose.onNodeWithText("执行结果").performClick()
        compose.onNodeWithText("独立结果").assertIsDisplayed(); compose.onNodeWithText("调用参数").assertDoesNotExist()
    }
    @Test fun providerModelsAndEffortShareOneDialogAndNoSummaryUsesCompletedThoughtStatus() {
        val model = ChatControl("模型", listOf(ChatOption("model-a", "模型A", section = "提供商A"), ChatOption("model-b", "模型B", section = "提供商B")), "model-a")
        val effort = ChatControl("思考强度", listOf(ChatOption("low", "低"), ChatOption("high", "高")), "low", ChatControlKind.REASONING)
        val provider = ChatControl("供应商", listOf(ChatOption("a", "提供商A")), "a", ChatControlKind.PROVIDER)
        var chosen by mutableStateOf("model-a")
        compose.setContent { KigttsTheme(ThemeSettings()) { Column {
            ChatBubble(ChatMessage("thought", ChatRole.ASSISTANT, "回答", hasReasoning = true))
            ChatComposerControls(PageBlock.Chat(emptyList(), "send", "stop", controls = listOf(provider, model.copy(selected = chosen), effort)), true) { action, _ -> chosen = action }
        } } }
        compose.onNodeWithText("已思考").assertIsDisplayed()
        compose.onNodeWithTag("chat-reasoning-slider").assertDoesNotExist()
        compose.onNodeWithTag("plugin-chat-control/PROVIDER").assertDoesNotExist()
        compose.onNodeWithTag("plugin-chat-control/MODEL").performClick()
        compose.onNodeWithText("提供商与模型").assertIsDisplayed()
        compose.onNodeWithText("提供商A").assertIsDisplayed(); compose.onNodeWithText("提供商B").assertIsDisplayed()
        compose.onNodeWithTag("chat-reasoning-slider").assertIsDisplayed()
        val positionA = compose.onNodeWithTag("chat-model-option/model-a").fetchSemanticsNode().boundsInRoot.top
        val positionB = compose.onNodeWithTag("chat-model-option/model-b").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("模型B").performClick(); compose.runOnIdle { assertEquals("model-b", chosen) }
        compose.onNodeWithTag("chat-model-option/model-b").onChild().assertIsSelected()
        assertEquals(positionA, compose.onNodeWithTag("chat-model-option/model-a").fetchSemanticsNode().boundsInRoot.top, .1f)
        assertEquals(positionB, compose.onNodeWithTag("chat-model-option/model-b").fetchSemanticsNode().boundsInRoot.top, .1f)
        compose.onNodeWithTag("chat-reasoning-slider").assertIsDisplayed()
        java.io.File("build/previews").mkdirs()
        compose.onNode(isDialog()).captureRoboImage("build/previews/agent-model-effort-dialog.png")
    }
}
