package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.*
import com.kigtts.next.ui.navigation.PageScaffold
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
class PluginPageRefreshUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun page(style: ManagementListStyle = ManagementListStyle.CATALOG) = PluginPage("store", "插件商店",
        listOf(PageBlock.Actions(listOf(PageAction("search", "搜索", symbol = "search")))),
        ManagementList("store", (0..16).map { ManagementItem("plugin-$it", "插件 $it", primaryAction = "open/$it") }, style = style),
        refreshAction = "refresh")

    @Test fun pullRefreshUsesToolbarWithoutMovingOrLockingTheCatalog() {
        var current by mutableStateOf(page())
        val calls = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("插件商店", {}) {
            PluginPageScreen(current) { action, _ -> calls += action; if (action == "refresh") current = current.copy(loading = true) }
        } } }
        val initial = compose.onNodeWithTag("managed-item/plugin-0").getUnclippedBoundsInRoot()
        fun pull() { compose.onNodeWithTag("managed-list/store").performTouchInput {
            swipeDown(startY = height * .2f, endY = height * .95f, durationMillis = 600)
        } }
        pull()
        compose.runOnIdle { assertEquals(listOf("refresh"), calls) }
        compose.onNodeWithTag("page-loading", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(initial, compose.onNodeWithTag("managed-item/plugin-0").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("plugin-page/search").assertIsEnabled().performClick()
        pull()
        compose.runOnIdle { assertEquals(listOf("refresh", "search"), calls); current = current.copy(loading = false) }
        pull()
        compose.runOnIdle { assertEquals(2, calls.count { it == "refresh" }); current = current.copy(loading = false) }
        compose.onNodeWithTag("page-loading").assertDoesNotExist()
    }

    @Test fun nestedManagementLoadingRegistrationCannotHideBackgroundRefresh() {
        var current by mutableStateOf(page(ManagementListStyle.STANDARD))
        compose.setContent { KigttsTheme(ThemeSettings()) { PageScaffold("插件来源", {}) {
            PluginPageScreen(current) { _, _ -> }
        } } }
        val initial = compose.onNodeWithTag("managed-item/plugin-0").getUnclippedBoundsInRoot()
        compose.runOnIdle { current = current.copy(loading = true) }
        compose.onNodeWithTag("page-loading", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(initial, compose.onNodeWithTag("managed-item/plugin-0").getUnclippedBoundsInRoot())
        compose.runOnIdle { current = current.copy(loading = false) }
        compose.onNodeWithTag("page-loading").assertDoesNotExist()
    }
}
