package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.plugins.PluginPageScreen
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PluginSearchHeaderUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun detailButtonStaysRightOfTheIdentityAndSharesTheCancellableProgressControl() {
        val page = mutableStateOf(PluginPage("detail", "详情", emptyList(),
            header = ManagementItem("plugin", "语音识别增强", "识别与实时字幕"),
            headerAction = ManagementProgressAction("获取", "download")))
        val calls = mutableListOf<String>()
        compose.setContent { MaterialTheme { PluginPageScreen(page.value) { action, _ -> calls += action } } }
        val button = compose.onNodeWithTag("managed-action/plugin")
        val title = compose.onNodeWithText("语音识别增强").getUnclippedBoundsInRoot()
        assertTrue(button.getUnclippedBoundsInRoot().left >= title.right)
        button.performClick()
        compose.runOnIdle { assertEquals(listOf("download"), calls)
            page.value = page.value.copy(headerAction = ManagementProgressAction("取消下载", "cancel", .4f, true)) }
        compose.onNodeWithContentDescription("取消下载").performClick()
        compose.runOnIdle { assertEquals(listOf("download", "cancel"), calls) }
        compose.onRoot().captureRoboImage("build/previews/store-detail-download.png")
    }
    @Test fun searchFocusesInlineDebouncesTypingClearsAndBackExits() {
        val calls = mutableListOf<String>(); var backs = 0
        compose.setContent { MaterialTheme { PluginPageScreen(
            PluginPage("search", "搜索插件", listOf(PageBlock.Text("搜索结果")), search = PluginSearch("", "query")),
            onBack = { backs++ }) { _, inputs -> calls += inputs.getValue("value") } } }
        compose.onNodeWithTag("plugin-search-input").assertIsFocused().performTextInput("语音")
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("语音"), calls) }
        compose.onNodeWithTag("plugin-search-input").performImeAction()
        compose.onNodeWithContentDescription("清除搜索").performClick()
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        compose.runOnIdle { assertEquals("", calls.last()) }
        compose.onRoot().captureRoboImage("build/previews/store-search-focused.png")
        compose.onNodeWithContentDescription("退出搜索").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(2, backs) }
    }
}
