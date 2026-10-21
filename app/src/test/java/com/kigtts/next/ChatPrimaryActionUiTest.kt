package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.ChatInputBar
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w480dp-h800dp-xhdpi")
class ChatPrimaryActionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun onlyOnePrimaryActionChangesBetweenSendStopAndQueueInThisConversation() {
        var running by mutableStateOf(false)
        val actions = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.width(450.dp)) {
            ChatInputBar(PageBlock.Chat(emptyList(), "send", "stop", running = running, backgroundRunning = running, queueAction = "queue"), true,
                { null }, { action, _ -> actions += action }) {}
        } } }
        compose.onNodeWithTag("plugin-chat-send").assertExists().assertIsNotEnabled()
        compose.onNodeWithTag("plugin-chat-stop").assertDoesNotExist()
        compose.runOnIdle { running = true }
        compose.onNodeWithTag("plugin-chat-stop").assertExists()
        compose.onNodeWithTag("plugin-chat-send").assertDoesNotExist()
        compose.onNodeWithTag("plugin-chat-input").performTextInput("稍后继续")
        compose.onNodeWithTag("plugin-chat-send").performClick()
        assertEquals(listOf("queue"), actions)
        compose.onNodeWithTag("plugin-chat-stop").performClick()
        assertEquals(listOf("queue", "stop"), actions)
        compose.runOnIdle { running = false }
        compose.onNodeWithTag("plugin-chat-send").assertExists()
        compose.onNodeWithTag("plugin-chat-stop").assertDoesNotExist()
    }
    @Test fun backgroundConversationDoesNotReplaceThisConversationsSendButton() {
        val actions = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) { ChatInputBar(
            PageBlock.Chat(emptyList(), "send", "stop", backgroundRunning = true, queueAction = "queue"), true,
            { null }, { action, _ -> actions += action }) {} } }
        compose.onNodeWithTag("plugin-chat-stop").assertDoesNotExist()
        compose.onNodeWithTag("plugin-chat-input").performTextInput("当前会话的新任务")
        compose.onNodeWithTag("plugin-chat-send").performClick()
        assertEquals(listOf("send"), actions)
    }
    @Test fun imageLongPressCallsCameraExactlyOnceWithoutOpeningThePicker() {
        val inputs = mutableListOf<Map<String, String>>()
        compose.setContent { KigttsTheme(ThemeSettings()) { ChatInputBar(
            PageBlock.Chat(emptyList(), "send", "stop", imageAction = "image"), true, { null }, { _, data -> inputs += data }) {} } }
        compose.onNodeWithTag("plugin-chat-image").performTouchInput { click() }
        assertEquals(listOf(emptyMap<String, String>()), inputs)
        compose.onNodeWithTag("plugin-chat-image").performTouchInput { longClick() }
        assertEquals(listOf(emptyMap<String, String>(), mapOf("source" to "camera")), inputs)
    }
}
