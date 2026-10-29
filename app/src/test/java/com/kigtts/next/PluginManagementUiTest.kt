package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.plugin.api.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PluginManagementUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun managementKeepsOneSettingsTargetAndMovesAboutIntoOverflow() {
        val plugin = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.settings", "测试语音服务", "包内说明", "2.0")
            override val contributions = PluginContributions(settingsEntries = listOf(
                SettingsEntry("main", "语音合成服务", "manager", PluginIcon.SPEAKER),
                SettingsEntry("voices", "语音包", "packs", PluginIcon.SPEAKER)))
            override fun page(pageId: String, context: PluginContext) = PluginPage(pageId, "主要设置", emptyList())
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
        }
        val model = AppViewModel(ApplicationProvider.getApplicationContext<Application>(), listOf(plugin))
        model.navigate(Destination.Plugins)
        compose.setContent { KigttsApp(model) }
        compose.onAllNodesWithContentDescription("设置").assertCountEquals(1)
        compose.onAllNodesWithText("管理已安装的插件").assertCountEquals(1)
        compose.onNodeWithTag("page-subtitle").assertTextEquals("管理已安装的插件")
        compose.onRoot().captureRoboImage("build/previews/plugin-manager-compact-settings.png")
        compose.onNodeWithText("语音包").assertDoesNotExist()
        compose.onNodeWithText("关于").assertDoesNotExist()
        compose.onNodeWithTag("managed-more/test.settings").performClick()
        compose.onNodeWithContentDescription("关于").performClick()
        compose.onNodeWithText("关闭").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.runOnIdle { assertEquals(Destination.PluginPage("test.settings", "manager"), model.state.value.destination) }
    }
    @Test fun productionManagementOffersInstallerAndNoBundledSpeechEngine() {
        val model = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        model.navigate(Destination.Plugins)
        compose.setContent { KigttsApp(model) }
        compose.onNodeWithContentDescription("安装插件").assertIsDisplayed()
        compose.onNodeWithText("还没有安装插件").assertIsDisplayed()
        compose.onNodeWithText("聆听占位插件").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/previews/plugin-manager-external-packages.png")
    }
    @Test fun declarativeInputUsesNativeDialogAndRoutesValuesToTheOwningPlugin() {
        var received: String? = null
        val plugin = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.form", "测试表单", "test")
            override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome = when (actionId) {
                "open" -> PluginOutcome.TextInput("插件配置值", "save", "原值")
                "save" -> { received = context.inputs["value"]; PluginOutcome.TextInput("第二项配置", "finish", "默认值") }
                else -> { received = context.inputs["value"]; PluginOutcome.RefreshUi }
            }
        }
        val model = AppViewModel(ApplicationProvider.getApplicationContext<Application>(), listOf(plugin))
        compose.setContent { KigttsApp(model) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.invoke("test.form", "open") }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("plugin-input-value").performTextReplacement("新值")
        compose.onNodeWithText("确定").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("新值", received) }
        compose.onNodeWithText("插件配置值").assertDoesNotExist()
        compose.onNodeWithTag("plugin-input-value").assertTextEquals("默认值")
        compose.onNodeWithText("确定").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("默认值", received) }
        compose.onNodeWithText("第二项配置").assertDoesNotExist()
    }
    @Test fun externalManagementPageSharesRendererAndRoutesAccessibleOrder() {
        var order = listOf("piper", "other"); var received: List<String>? = null
        val plugin = object : KigPlugin {
            override val descriptor = PluginDescriptor("test.library", "语音包管理", "test")
            override fun page(pageId: String, context: PluginContext) = PluginPage("packs", "语音包管理", emptyList(), ManagementList("cross-engine",
                order.map { ManagementItem(it, if (it == "piper") "旧版 Piper 语音" else "其它引擎声音", "跨引擎列表", PluginIcon.SPEAKER) }, "save-order"))
            override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
                order = PluginJson.decodeFromString(context.inputs.getValue("order")); received = order; return PluginOutcome.RefreshUi
            }
        }
        val model = AppViewModel(ApplicationProvider.getApplicationContext<Application>(), listOf(plugin))
        model.navigate(Destination.PluginPage("test.library", "packs"))
        compose.setContent { KigttsApp(model) }
        val move = compose.onNodeWithTag("managed-grip/piper", useUnmergedTree = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].single { it.label == "下移" }
        compose.runOnIdle { move.action() }
        compose.waitForIdle()
        assertEquals(listOf("other", "piper"), received)
        val first = compose.onNodeWithTag("managed-item/other").getUnclippedBoundsInRoot()
        val last = compose.onNodeWithTag("managed-item/piper").getUnclippedBoundsInRoot()
        assertTrue(first.top < last.top)
        compose.onRoot().captureRoboImage("build/previews/unified-voice-package-page.png")
    }
}
