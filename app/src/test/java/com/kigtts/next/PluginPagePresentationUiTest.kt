package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.ui.navigation.LocalPageActive
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
class PluginPagePresentationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun entryRunsOncePerVisitAfterBusyAndDetailsRemainExpandedOnRefresh() {
        com.kigtts.next.ui.components.MaterialSymbols.initialize(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val page = mutableStateOf(PluginPage("store", "商店", listOf(PageBlock.Group("权限说明", listOf(PageBlock.Text("用于下载插件")), collapsible = true)),
            header = ManagementItem("plugin", "插件商店", "发现与安装插件", symbol = "storefront"), onEnterAction = "store-enter"))
        val active = mutableStateOf(true); val busy = mutableStateOf(true); val calls = mutableListOf<String>()
        compose.setContent { KigttsTheme(com.kigtts.next.theme.ThemeSettings()) { CompositionLocalProvider(LocalPageActive provides active.value) {
            PluginPageScreen(page.value, busy.value) { action, _ -> calls += action }
        } } }
        compose.runOnIdle { assertTrue(calls.isEmpty()); busy.value = false }
        compose.runOnIdle { assertEquals(listOf("store-enter"), calls) }
        compose.onNodeWithTag("plugin-page-header").assertIsDisplayed()
        compose.onNodeWithText("用于下载插件").assertDoesNotExist()
        compose.onNodeWithTag("plugin-group/权限说明").performClick()
        compose.onNodeWithText("用于下载插件").assertIsDisplayed()
        compose.runOnIdle { page.value = page.value.copy(title = "商店 · 已更新"); busy.value = true }
        compose.runOnIdle { busy.value = false }
        compose.onNodeWithText("用于下载插件").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, calls.size); active.value = false }
        compose.runOnIdle { active.value = true }
        compose.runOnIdle { assertEquals(2, calls.size) }
        compose.onRoot().captureRoboImage("build/previews/store-details-permissions.png")
        compose.onNodeWithTag("plugin-group/权限说明").performClick()
        compose.onNodeWithText("用于下载插件").assertDoesNotExist()
    }
}
