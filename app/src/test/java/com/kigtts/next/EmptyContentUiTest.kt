package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.components.EmptyContent
import com.kigtts.next.ui.management.ManagedItemList
import com.kigtts.next.ui.plugins.*
import com.kigtts.next.theme.*
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
class EmptyContentUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun ink(frame: String): Double {
        val path = "build/previews/empty-opacity-$frame.png"
        compose.onNodeWithTag("viewport").captureRoboImage(path)
        val pixels = android.graphics.BitmapFactory.decodeFile(path)
        var sum = 0.0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) sum += 1.0 - android.graphics.Color.red(pixels.getPixel(x,y)) / 255.0
        pixels.recycle()
        return sum
    }

    @Test fun emptyLayerFadesBothWaysAndSurvivesRapidReversalWithoutMovingItsCenter() {
        var visible by mutableStateOf(false)
        var small by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(surface = Color.White, onSurface = Color.Black, onSurfaceVariant = Color.Black)) {
                Box(Modifier.fillMaxSize()) { Surface(Modifier.size(320.dp, if (small) 110.dp else 440.dp).testTag("viewport")) {
                    EmptyContent(visible, Icons.Default.Collections, "还没有内容", description = "添加后会显示在这里")
                } }
            }
        }
        compose.onNodeWithText("还没有内容").assertDoesNotExist()
        compose.runOnIdle { visible = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        val entering = ink("entering")
        compose.mainClock.advanceTimeBy(240)
        val full = ink("full")
        assertTrue("Enter must have an intermediate opacity: entering=$entering full=$full", entering > 0 && entering < full*.95)
        val content = compose.onNodeWithText("还没有内容").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.center.x, content.center.x, 2f)
        assertEquals(viewport.center.y, content.center.y, 2f)
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        val leaving = ink("leaving")
        assertTrue("Exit must fade instead of disappearing", leaving > 0 && leaving < full*.95)
        compose.runOnIdle { visible = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(240)
        assertEquals(full, ink("reversed"), full*.01)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { small = true }
        compose.waitUntil(5_000) { compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot.height < 300 }
        compose.onNodeWithText("还没有内容").assertIsDisplayed()
        compose.onNodeWithText("添加后会显示在这里", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithText("还没有内容").assertDoesNotExist()
    }

    @Test fun chatEmptinessTracksMessagesAndClearWithoutCoveringTheComposer() {
        var messages by mutableStateOf(emptyList<ChatMessage>())
        var running by mutableStateOf(false)
        val cache = ChatHistoryCache()
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.size(450.dp,720.dp)) {
            PluginChatScreen(PageBlock.Chat(messages,"send","stop",running = running, conversationId = "same",
                historyAction = "history", historyTotal = messages.size),true,{_,_->},historyCache = cache) {}
        } } }
        compose.onNodeWithText("开始新的对话").assertIsDisplayed()
        val empty = compose.onNodeWithText("开始新的对话").fetchSemanticsNode().boundsInRoot
        val history = compose.onNodeWithTag("plugin-chat-history").fetchSemanticsNode().boundsInRoot
        assertEquals(history.center.y, empty.center.y, 2f)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/empty-chat.png")
        compose.runOnIdle { messages = listOf(ChatMessage("one", ChatRole.USER, "你好")) }
        compose.onNodeWithText("开始新的对话").assertDoesNotExist()
        compose.onNodeWithText("你好").assertIsDisplayed()
        compose.runOnIdle { messages = emptyList() }
        compose.onNodeWithText("你好").assertDoesNotExist()
        compose.onNodeWithText("开始新的对话").assertIsDisplayed()
        compose.runOnIdle { running = true }
        compose.onNodeWithText("开始新的对话").assertDoesNotExist()
    }

    @Test fun managementEmptyBodyCentersBelowFiltersAndRestoresAfterLastItemRemoved() {
        var entries by mutableStateOf(emptyList<ManagementItem>())
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.size(450.dp,720.dp)) {
            ManagedItemList(ManagementList("test",entries,emptyText = "还没有条目"),invoke = {_,_->}) {
                Text("筛选区域", Modifier.height(110.dp))
            }
        } } }
        compose.onNodeWithText("还没有条目").assertIsDisplayed()
        val head = compose.onNodeWithText("筛选区域").fetchSemanticsNode().boundsInRoot
        val empty = compose.onNodeWithText("还没有条目").fetchSemanticsNode().boundsInRoot
        assertTrue(empty.top > head.bottom)
        compose.runOnIdle { entries = listOf(ManagementItem("one","条目一")) }
        compose.onNodeWithText("还没有条目").assertDoesNotExist()
        compose.onNodeWithText("条目一").assertIsDisplayed()
        compose.runOnIdle { entries = emptyList() }
        compose.onNodeWithText("还没有条目").assertIsDisplayed()
    }
}
