package com.kigtts.next

import androidx.activity.ComponentActivity
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
}
