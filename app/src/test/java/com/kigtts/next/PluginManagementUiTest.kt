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
    @Test fun productionManagementOffersInstallerAndNoBundledSpeechEngine() {
        val model = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        model.navigate(Destination.Plugins)
        compose.setContent { KigttsApp(model) }
        compose.onNodeWithText("安装 .kigplugin").assertIsDisplayed()
        compose.onNodeWithText("尚未安装语音引擎插件").assertIsDisplayed()
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
}
