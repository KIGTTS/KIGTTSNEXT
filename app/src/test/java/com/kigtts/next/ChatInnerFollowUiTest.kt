package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.ChatBubble
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w480dp-h800dp-xhdpi")
class ChatInnerFollowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun expandedThoughtFollowsItsOwnEndButUserReadingOlderTextKeepsTheirPosition() {
        var tail by mutableStateOf("思考末尾")
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.width(450.dp).height(640.dp)) {
            ChatBubble(ChatMessage("thought", ChatRole.ASSISTANT, "", reasoning = (1..50).joinToString("\n\n") { "思考片段 $it" } + "\n\n" + tail,
                thinking = true, state = ChatMessageState.STREAMING))
        } } }
        compose.onNodeWithTag("chat-thought/thought").performClick()
        compose.onNodeWithText(tail).assertIsDisplayed()
        compose.onNodeWithTag("chat-reasoning-scroll/thought").performTouchInput { swipeDown() }
        compose.onNodeWithText(tail).assertIsNotDisplayed()
        repeat(8) { index -> compose.runOnIdle { tail = "新思考末尾 $index" }; compose.waitForIdle() }
        compose.onNodeWithText(tail).assertIsNotDisplayed()
        compose.onNodeWithText(tail).performScrollTo()
        compose.onNodeWithText(tail).assertIsDisplayed()
        compose.runOnIdle { tail += "\n\n继续思考" }
        compose.onNodeWithText("继续思考").assertIsDisplayed()
    }
}
