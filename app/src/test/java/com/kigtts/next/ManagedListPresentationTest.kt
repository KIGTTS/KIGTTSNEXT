package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.ProvideButtonFeedback
import com.kigtts.next.ui.management.*
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ManagedListPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun initializeIcons() { com.kigtts.next.ui.components.MaterialSymbols.initialize(compose.activity) }
    @Test fun mixedSourceArtworkAndVectorIconsOccupyTheSameStandardListSize() {
        compose.setContent { KigttsTheme(ThemeSettings()) {
            ManagedItemList(ManagementList("sources", listOf(
                ManagementItem("image", "官方源", image = PluginResource("missing.svg")),
                ManagementItem("vector", "自定义源", icon = PluginIcon.SETTINGS))), invoke = { _, _ -> })
        } }
        val image = compose.onNodeWithTag("managed-avatar/image", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val vector = compose.onNodeWithTag("managed-avatar/vector", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(40f, (image.right - image.left).value, .1f)
        assertEquals(image.right - image.left, vector.right - vector.left); assertEquals(image.bottom - image.top, vector.bottom - vector.top)
    }
    private val actions = listOf(PageAction("pin", "置顶", PluginIcon.PIN), PageAction("info", "详细信息", PluginIcon.INFO),
        PageAction("settings", "音频设置", PluginIcon.SETTINGS), PageAction("export", "导出语音包", PluginIcon.EXPORT),
        PageAction("delete", "删除语音包", symbol = "delete"))

    @Test fun secondaryActionsRevealBesideTheRowAndDoNotSelectTheVoice() {
        val calls = mutableListOf<String>()
        var selected by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) { ProvideButtonFeedback(true) {
            ManagedItemList(ManagementList("voices", listOf(ManagementItem("voice", "流萤", "测试语音包", selected = selected,
                badges = listOf("Piper"), primaryAction = "use", selection = ManagementSelection("use"), actions = actions)), "order"),
                invoke = { action, _ -> calls += action; if (action == "use") selected = true })
        } } }
        val radio = compose.onNodeWithTag("managed-select/voice", useUnmergedTree = true)
        radio.assertIsNotSelected()
        val row = compose.onNodeWithTag("managed-item/voice")
        val bounds = row.getUnclippedBoundsInRoot()
        compose.onNodeWithTag("reveal-action/voice/delete").assertDoesNotExist()
        compose.onNodeWithTag("managed-more/voice").performClick()
        val button = compose.onNodeWithTag("reveal-action/voice/export", useUnmergedTree = true)
        assertTrue(button.getUnclippedBoundsInRoot().top < radio.getUnclippedBoundsInRoot().bottom)
        val delete = compose.onNodeWithTag("reveal-action/voice/delete").getUnclippedBoundsInRoot()
        assertTrue("M3E side actions should be tall capsules", delete.bottom - delete.top > delete.right - delete.left)
        assertTrue(delete.left > button.getUnclippedBoundsInRoot().left)
        assertEquals(bounds.bottom - bounds.top, row.getUnclippedBoundsInRoot().bottom - row.getUnclippedBoundsInRoot().top)
        compose.onRoot().captureRoboImage("build/previews/managed-voice-list-light.png")
        button.performClick()
        assertEquals(listOf("export"), calls); radio.assertIsNotSelected()
        radio.performClick(); radio.assertIsSelected(); assertEquals(listOf("export", "use"), calls)
        compose.onNodeWithTag("managed-more/voice").assertIsDisplayed()
    }

    @Test fun narrowListsOverflowOnlyTheActionsThatDoNotFitAndAdaptWhenWidened() {
        var width by mutableStateOf(220.dp)
        val calls = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.DARK)) { ProvideButtonFeedback(true) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { Box(Modifier.width(width).fillMaxHeight()) {
                ManagedItemList(ManagementList("narrow", listOf(ManagementItem("voice", "语音包", actions = actions))),
                    invoke = { action, _ -> calls += action })
            } } }
        } } }
        compose.onNodeWithTag("reveal-action/voice/pin").assertDoesNotExist()
        compose.onNodeWithTag("reveal-action/voice/delete").assertDoesNotExist()
        compose.onNodeWithTag("managed-more/voice").performClick()
        compose.onNodeWithTag("reveal-action/voice/delete").assertIsDisplayed()
        compose.onNodeWithTag("reveal-overflow/voice").performClick()
        compose.onNodeWithText("导出语音包").performClick()
        assertEquals(listOf("export"), calls)
        compose.runOnIdle { width = 393.dp }
        compose.onNodeWithTag("managed-more/voice").performClick()
        compose.onNodeWithTag("reveal-overflow/voice").assertDoesNotExist()
        compose.onNodeWithTag("reveal-action/voice/delete").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/managed-voice-list-dark.png")
    }

    @Test fun adjacentCornersRespectGroupBoundariesAndVisibleOrder() {
        val items = listOf(ManagementItem("a", "A", group = "pinned"), ManagementItem("b", "B"), ManagementItem("c", "C"), ManagementItem("d", "D"))
        assertEquals(listOf(SettingPosition.SINGLE, SettingPosition.FIRST, SettingPosition.MIDDLE, SettingPosition.LAST),
            items.indices.map { managedItemPosition(items, it) })
    }

    @Test @Config(qualifiers = "zh-rCN-w851dp-h393dp-land-xhdpi")
    fun landscapeRowsKeepControlsAndFooterVisibleWithWideArtworkAndLongNames() {
        compose.setContent { KigttsTheme(ThemeSettings()) { ProvideButtonFeedback(true) {
            ManagedItemList(ManagementList("landscape", (1..3).map { index -> ManagementItem("voice$index", "流萤 · 多语种声音 $index",
                "用于日常朗读的语音包", image = PluginResource("avatar.png"), badges = listOf("Piper"),
                selected = index == 2, selection = ManagementSelection("use/$index"), actions = actions) }, "order"), invoke = { _, _ -> })
        } } }
        compose.onNodeWithTag("managed-select/voice2", useUnmergedTree = true).assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("managed-more/voice2").performClick()
        compose.onNodeWithTag("reveal-action/voice2/export", useUnmergedTree = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/managed-voice-list-landscape.png")
    }

    @Test fun disabledProviderCannotBeSelectedWhileItsDetailsRemainAvailable() {
        val calls = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            ManagedItemList(ManagementList("disabled", listOf(ManagementItem("off", "已停用引擎", primaryAction = "details",
                selection = ManagementSelection("use", false)))), invoke = { action, _ -> calls += action })
        } }
        compose.onNodeWithTag("managed-select/off", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("managed-item/off").performClick()
        assertEquals(listOf("details"), calls)
    }

    @Test fun dualActionRowsSelectBeforeOpeningAndRadioUsesTheSameRule() {
        val calls = mutableListOf<String>()
        var selected by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            ManagedItemList(ManagementList("engines", listOf(ManagementItem("cloud", "云端声音", selected = selected,
                primaryAction = "details", selection = ManagementSelection("use")))), invoke = { action, _ ->
                calls += action; if (action == "use") selected = true
            })
        } }
        compose.onNodeWithText("云端声音").performClick()
        compose.onNodeWithTag("managed-select/cloud", useUnmergedTree = true).assertIsSelected()
        assertEquals(listOf("use"), calls)
        compose.onNodeWithText("云端声音").performClick()
        assertEquals(listOf("use", "details"), calls)
        compose.runOnIdle { selected = false }
        compose.onNodeWithTag("managed-select/cloud", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("managed-select/cloud", useUnmergedTree = true).performClick()
        assertEquals(listOf("use", "details", "use", "details"), calls)
    }

    @Test fun selectionOnlyRowsStillWorkWithoutAPrimaryAction() {
        val calls = mutableListOf<String>()
        var selected by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) {
            ManagedItemList(ManagementList("choices", listOf(ManagementItem("choice", "选项", selected = selected,
                selection = ManagementSelection("use")))), invoke = { action, _ -> calls += action; selected = true })
        } }
        compose.onNodeWithText("选项").performClick()
        compose.onNodeWithText("选项").performClick()
        assertEquals(listOf("use", "use"), calls)
    }

    @Test fun swipingRevealsWithoutExecutingAndPinningIsOnlyAvailableWhenSupported() {
        val calls = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) {
            ManagedItemList(ManagementList("swipe", listOf(
                ManagementItem("a", "支持置顶", primaryAction = "details", actions = actions),
                ManagementItem("b", "普通条目", actions = actions.filterNot { it.icon == PluginIcon.PIN }))),
                invoke = { action, _ -> calls += action })
        } }
        compose.onNodeWithTag("reveal-row/a").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("reveal-action/a/delete").assertIsDisplayed()
        assertTrue("Even a full swipe cannot delete or activate an item", calls.isEmpty())
        compose.onNodeWithTag("managed-more/b").performClick()
        compose.onNodeWithTag("reveal-action/a/delete").assertDoesNotExist()
        compose.onNodeWithTag("reveal-action/b/delete").assertIsDisplayed()
        compose.onNodeWithTag("managed-more/b").performClick()
        val closed = compose.onNodeWithTag("managed-item/b").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("reveal-row/b").performTouchInput { swipeRight() }
        assertEquals(closed, compose.onNodeWithTag("managed-item/b").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("reveal-row/a").performTouchInput { swipeRight() }
        assertEquals(listOf("pin"), calls)
        compose.onNodeWithTag("reveal-action/a/pin").assertDoesNotExist()
    }

    @Test fun filterTabsHaveCompactTopInsetAndActionsDoNotAddASecondButtonRow() {
        compose.setContent { KigttsTheme(ThemeSettings()) {
            ManagedItemList(ManagementList("tabs", listOf(ManagementItem("voice", "声音", actions = actions)),
                filters = listOf(ManagementFilter("all", "全部", "all", true))), invoke = { _, _ -> })
        } }
        val list = compose.onNodeWithTag("managed-list/tabs").getUnclippedBoundsInRoot()
        val tab = compose.onNodeWithText("全部").getUnclippedBoundsInRoot()
        assertTrue("Filter label must begin within the first 24dp", (tab.top - list.top).value <= 24f)
        val row = compose.onNodeWithTag("managed-item/voice").getUnclippedBoundsInRoot()
        assertEquals(80f, (row.bottom - row.top).value, 1f)
    }
}
