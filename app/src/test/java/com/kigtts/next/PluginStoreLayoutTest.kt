package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.plugins.PluginPageScreen
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.LocalPageSettled
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
class PluginStoreLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun catalog(wide: Boolean) {
        val actions = mutableListOf<String>()
        val entries = listOf("语音朗读", "云端语音").mapIndexed { index, title ->
            ManagementItem("item$index", title, "选择适合自己的声音", group = "语音", primaryAction = "open/$index")
        }
        val list = ManagementList("store/all", entries, style = ManagementListStyle.CATALOG,
            sectionActions = mapOf("语音" to PageAction("more", "更多")))
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PluginPageScreen(PluginPage("store", "插件商店", emptyList(), list)) { action, _ -> actions += action }
        } }
        compose.onNodeWithTag("catalog-section-card/语音").assertIsDisplayed()
        compose.onNodeWithTag("catalog-section/语音").assertIsDisplayed()
        val first = compose.onNodeWithTag("managed-item/item0").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("managed-item/item1").getUnclippedBoundsInRoot()
        if (wide) { assertEquals(first.top, second.top); assertTrue(second.left > first.left) }
        else { assertEquals(first.left, second.left); assertTrue(second.top > first.top) }
        compose.onNodeWithTag("managed-item/item1").performClick()
        compose.onNodeWithText("更多").performClick()
        assertEquals(listOf("open/1", "more"), actions)
        compose.onRoot().captureRoboImage("build/previews/store-nested-${if (wide) "tablet" else "phone"}.png")
    }

    @Test fun phoneSectionsUseSettingsCardsAndKeepEntriesInteractive() = catalog(false)
    @Test @Config(qualifiers = "zh-rCN-w840dp-h1000dp-xhdpi")
    fun tabletSectionsKeepTwoColumns() = catalog(true)

    @Test fun aLargeCatalogSectionOnlyComposesVisibleEntries() {
        val items = List(80) { ManagementItem("large-$it", "项目 $it", group = "分类") }
        val list = ManagementList("store/large", items, style = ManagementListStyle.CATALOG,
            sectionActions = mapOf("分类" to PageAction("more", "更多")))
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PluginPageScreen(PluginPage("store", "拓展市场", emptyList(), list)) { _, _ -> }
        } }
        compose.onNodeWithTag("managed-item/large-0").assertIsDisplayed()
        compose.onNodeWithTag("managed-item/large-79").assertDoesNotExist()
        compose.onNodeWithTag("managed-list/store/large").performScrollToKey("large-79")
        compose.onNodeWithTag("managed-item/large-79").assertIsDisplayed()
    }

    @Test fun detailBodyHasReadingInsetsInsideItsCard() {
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PluginPageScreen(PluginPage("package", "云端语音", listOf(
                PageBlock.Group("介绍", listOf(PageBlock.Text("选择喜欢的声音，添加到自己的语音包列表。"),
                    PageBlock.Markdown("支持多个账户，按需配置。")))))) { _, _ -> }
        } }
        val title = compose.onNodeWithText("介绍").getUnclippedBoundsInRoot()
        val body = compose.onNodeWithText("选择喜欢的声音，添加到自己的语音包列表。").getUnclippedBoundsInRoot()
        assertTrue(body.left > title.left)
        compose.onNodeWithText("支持多个账户，按需配置。").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/store-detail-insets.png")
    }

    @Test fun theMarketDetailShowsItsHeaderThenLoadsVisibleSectionsAndKeepsItsPosition() {
        val settled = mutableStateOf(false)
        val active = mutableStateOf(true)
        val calls = mutableListOf<String>()
        val page = PluginPage("package", "预览插件", listOf(
            PageBlock.Group("介绍", listOf(PageBlock.Markdown("**带格式的介绍**"))),
        ) + List(36) { PageBlock.Group("区块 $it", listOf(PageBlock.Text("版本内容 $it"))) },
            header = ManagementItem("preview", "预览插件"), onEnterAction = "open-detail")
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CompositionLocalProvider(LocalPageSettled provides settled.value, LocalPageActive provides active.value) {
                PluginPageScreen(page, progressiveMarket = true) { action, _ -> calls += action }
            }
        } }
        compose.onNodeWithTag("plugin-page-header").assertIsDisplayed()
        compose.onAllNodesWithTag("market-page-placeholder")[0].assertIsDisplayed()
        compose.onNodeWithText("介绍").assertDoesNotExist()
        assertTrue(calls.isEmpty())
        compose.runOnIdle { settled.value = true }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("带格式的介绍").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("open-detail"), calls)
        compose.onNodeWithText("区块 35").assertDoesNotExist()
        compose.onNodeWithTag("market-article").performScrollToIndex(37)
        compose.onNodeWithText("区块 35").assertIsDisplayed()
        compose.runOnIdle { active.value = false; settled.value = false }
        compose.runOnIdle { active.value = true }
        compose.onNodeWithTag("market-page-placeholder").assertDoesNotExist()
        compose.onNodeWithText("区块 35").assertIsDisplayed()
        compose.runOnIdle { settled.value = true }
        compose.waitForIdle()
        assertEquals(listOf("open-detail"), calls)
    }

    @Test fun catalogRowsMountAfterEntrance() {
        val settled = mutableStateOf(false)
        val list = ManagementList("store/all", listOf(ManagementItem("test-entry", "插件资源")),
            style = ManagementListStyle.CATALOG)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            CompositionLocalProvider(LocalPageSettled provides settled.value) {
                PluginPageScreen(PluginPage("store", "拓展市场", emptyList(), list), progressiveMarket = true) { _, _ -> }
            }
        } }
        compose.onAllNodesWithTag("market-page-placeholder")[0].assertIsDisplayed()
        compose.onNodeWithTag("managed-item/test-entry").assertDoesNotExist()
        compose.runOnIdle { settled.value = true }
        compose.onNodeWithTag("managed-item/test-entry").assertIsDisplayed()
    }
}
