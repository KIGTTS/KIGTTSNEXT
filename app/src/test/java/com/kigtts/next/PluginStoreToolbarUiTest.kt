package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.navigation.PageScaffold
import com.kigtts.next.ui.plugins.PluginPageScreen
import com.kigtts.plugin.api.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PluginStoreToolbarUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun checkToolbar(preview: String) {
        var extra by mutableStateOf(false)
        val calls = mutableListOf<String>()
        val header = PageBlock.Actions(listOf(
            PageAction("search", "搜索", PluginIcon.NONE, "search"),
            PageAction("library", "我的", PluginIcon.NONE, "apps"),
            PageAction("store-settings", "设置", PluginIcon.NONE, "settings")))
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PageScaffold("插件商店", {}) {
                PluginPageScreen(PluginPage("store", "插件商店", listOf(header) +
                    if (extra) listOf(PageBlock.Actions(listOf(PageAction("show-plan", "查看安装计划")))) else emptyList(),
                    ManagementList("store", listOf(ManagementItem("draw", "画板工具", subtitle = "创作与保存画板，支持主题和无限画布。", icon = PluginIcon.MENU, group = "创作", primaryAction = "package/draw")), style = ManagementListStyle.CATALOG))) { action, _ -> calls += action }
            }
        } }
        listOf("search", "library", "store-settings").forEach {
            compose.onNodeWithTag("plugin-page/$it").assertIsDisplayed().performClick()
        }
        assertEquals(listOf("search", "library", "store-settings"), calls)
        compose.onNodeWithText("创作").assertIsDisplayed()
        compose.onNodeWithText("创作与保存画板，支持主题和无限画布。").assertIsDisplayed()
        compose.onNodeWithTag("managed-item/draw").performClick()
        assertEquals("package/draw", calls.last())
        compose.runOnIdle { extra = true }
        listOf("search", "library", "store-settings").forEach {
            compose.onNodeWithTag("plugin-page/$it").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("更多页面操作").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/$preview.png")
    }

    @Test fun storeHeaderKeepsThreeDirectActionsWhenAnotherJobAddsAnAction() = checkToolbar("store-portrait-toolbar")

    @Test
    @Config(qualifiers = "zh-rCN-w852dp-h393dp-land-xxhdpi")
    fun landscapeKeepsTheSameDirectActions() = checkToolbar("store-landscape-toolbar")
}
