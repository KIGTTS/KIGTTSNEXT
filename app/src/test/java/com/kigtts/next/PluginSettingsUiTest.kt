package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
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
class PluginSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun step(frames: Int) { repeat(frames) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } }

    @Test fun pluginSettingsUseGroupedRowsHideRepeatedTitleAndRouteTogglesWithoutEmptyMenus() {
        var busy by mutableStateOf(false); var active by mutableStateOf(true)
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        val page = PluginPage("settings", "合成服务", listOf(PageBlock.Text("合成服务", true)), ManagementList("settings", listOf(
            ManagementItem("voice", "声音", "清朗", group = "朗读", primaryAction = "sounds"),
            ManagementItem("rate", "语速", "1×", group = "朗读", primaryAction = "rate"),
            ManagementItem("resident", "保持加载", group = "加载方式", toggle = ManagementToggle(false, "resident")))))
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalPageActive provides active) {
            PluginPageScreen(page, busy) { action, input -> calls += action to input }
        } } }
        step(35)
        compose.onNodeWithText("合成服务").assertDoesNotExist()
        compose.onNodeWithText("朗读").assertIsDisplayed()
        compose.onNodeWithTag("managed-more/voice").assertDoesNotExist()
        compose.onNodeWithTag("managed-item/voice").performClick()
        compose.onNodeWithTag("managed-toggle/resident").performClick()
        assertEquals(listOf("sounds" to emptyMap(), "resident" to mapOf("checked" to "true")), calls)
        compose.runOnIdle { busy = true }; step(5)
        // Busy requests keep the existing appearance; callbacks still reject a second operation.
        compose.onNodeWithTag("managed-item/voice").performClick()
        compose.onNodeWithTag("managed-toggle/resident").performClick()
        assertEquals(2, calls.size)
        compose.runOnIdle { busy = false; active = false }; step(5)
        compose.onNodeWithTag("managed-item/voice").assertIsNotEnabled()
        compose.runOnIdle { active = true }; step(20)
        compose.onRoot().captureRoboImage("build/previews/plugin-grouped-settings.png")
    }
}
