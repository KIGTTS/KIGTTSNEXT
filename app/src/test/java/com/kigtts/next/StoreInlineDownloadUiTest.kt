package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
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
class StoreInlineDownloadUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun check(dark: Boolean) {
        var action by mutableStateOf(ManagementProgressAction("获取", "download"))
        val calls = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
            PluginPageScreen(PluginPage("store", "插件商店", emptyList(), ManagementList("store", listOf(
                ManagementItem("agent", "AI 助手", "聊天、查资料，制作快捷文本和小工具", symbol = "robot_2", primaryAction = "detail"),
                ManagementItem("led", "LED 灯珠字幕", "把字幕变成发光灯板", symbol = "lightbulb", primaryAction = "led-detail")),
                style = ManagementListStyle.CATALOG, itemActions = mapOf("agent" to action, "led" to ManagementProgressAction("已安装")))), false) { id, _ -> calls += id }
        } }
        compose.onNodeWithText("获取").performClick()
        assertEquals(listOf("download"), calls)
        compose.runOnIdle { action = ManagementProgressAction("取消下载", "cancel", .42f, true) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("取消下载").assertIsDisplayed().performClick()
        assertEquals(listOf("download", "cancel"), calls)
        compose.onNodeWithText("AI 助手").assertIsDisplayed()
        java.io.File("build/previews").mkdirs()
        compose.onRoot().captureRoboImage("build/previews/store-inline-${if (dark) "landscape-dark" else "portrait-light"}.png")
        compose.runOnIdle { action = ManagementProgressAction("安装", "install") }
        compose.waitForIdle()
        compose.onNodeWithText("安装").performClick()
        assertEquals("install", calls.last())
    }
    @Test fun portrait() = check(false)
    @Test @Config(qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi") fun landscape() = check(true)
}
