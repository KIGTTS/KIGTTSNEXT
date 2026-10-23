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
    private val actions = listOf(PageAction("pin", "置顶", PluginIcon.PIN), PageAction("info", "详细信息", PluginIcon.INFO),
        PageAction("settings", "音频设置", PluginIcon.SETTINGS), PageAction("export", "导出语音包", PluginIcon.EXPORT),
        PageAction("delete", "删除语音包", symbol = "delete"))

    @Test fun secondaryActionsStayBelowTheHeaderAndDoNotSelectTheVoice() {
        val calls = mutableListOf<String>()
        var selected by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) { ProvideButtonFeedback(true) {
            ManagedItemList(ManagementList("voices", listOf(ManagementItem("voice", "流萤", "测试语音包", selected = selected,
                badges = listOf("Piper"), primaryAction = "use", selection = ManagementSelection("use"), actions = actions)), "order"),
                invoke = { action, _ -> calls += action; if (action == "use") selected = true })
        } } }
        val radio = compose.onNodeWithTag("managed-select/voice", useUnmergedTree = true)
        radio.assertIsNotSelected()
        val button = compose.onNodeWithTag("managed-action/voice/export", useUnmergedTree = true)
        assertTrue(button.getUnclippedBoundsInRoot().top >= radio.getUnclippedBoundsInRoot().bottom)
        button.performClick()
        assertEquals(listOf("export"), calls); radio.assertIsNotSelected()
        radio.performClick(); radio.assertIsSelected(); assertEquals(listOf("export", "use"), calls)
        compose.onNodeWithTag("managed-more/voice").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/previews/managed-voice-list-light.png")
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
        compose.onNodeWithTag("managed-action/voice/pin").assertIsDisplayed()
        compose.onNodeWithTag("managed-action/voice/delete").assertDoesNotExist()
        compose.onNodeWithTag("managed-more/voice").performClick()
        compose.onNodeWithText("删除语音包").performClick()
        assertEquals(listOf("delete"), calls)
        compose.runOnIdle { width = 393.dp }
        compose.onNodeWithTag("managed-more/voice").assertDoesNotExist()
        compose.onNodeWithTag("managed-action/voice/delete").assertIsDisplayed()
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
        compose.onNodeWithTag("managed-action/voice2/export", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("managed-more/voice2").assertDoesNotExist()
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
}
