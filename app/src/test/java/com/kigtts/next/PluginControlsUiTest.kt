package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.ui.plugins.PluginControlsDialog
import com.kigtts.plugin.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PluginControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun nativePickerSliderAndSegmentedControlsRemainOpenAndResetWithoutClosing() {
        val changes = mutableListOf<Pair<String, String?>>()
        var dismissed = false
        val revision = mutableIntStateOf(0)
        val request = PluginOutcome.Controls("LED 样式", listOf(
            PluginControl.Color("字幕色", "color", 0xFFFFFFFF),
            PluginControl.Slider("灯珠大小", "size", 58f, 10f, 100f, suffix = "%"),
            PluginControl.Toggle("发光", "glow", true),
            PluginControl.Choice("灯珠形状", "shape", listOf(PluginControlOption("circle", "圆形"),
                PluginControlOption("square", "方形")), "circle")), "reset")
        compose.setContent { MaterialTheme { PluginControlsDialog(request, { action, value ->
            changes += action to value
            if (action == "reset") revision.intValue++
        }, { dismissed = true }, revision.intValue) } }
        compose.onNodeWithTag("plugin-color-color").performClick()
        compose.onNodeWithText("HEX 颜色").assertExists()
        compose.onNodeWithContentDescription("预设颜色 #FF6B6B").performScrollTo().performClick()
        compose.onNodeWithText("应用颜色").performClick(); compose.waitForIdle()
        assertEquals("color" to 0xFFFF6B6B.toString(), changes.last())
        compose.onNodeWithContentDescription("灯珠大小").performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        assertEquals("size" to "75.0", changes.last())
        compose.onNodeWithContentDescription("灯珠形状：方形").performScrollTo().performClick()
        assertEquals("shape" to "square", changes.last())
        compose.onNodeWithText("恢复默认").performClick(); compose.waitForIdle()
        assertEquals("reset" to null, changes.last()); assertFalse(dismissed)
        compose.onNodeWithContentDescription("灯珠大小").performScrollTo()
        compose.onNodeWithText("58%").assertExists()
        // The reset button must not put MotionAlertDialog into its terminal closing state.
        compose.onNodeWithText("完成").performClick(); compose.waitForIdle(); assertTrue(dismissed)
    }
    @Test @Config(qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
    fun longSettingsScrollAndSelectorRemainAvailableInLandscape() {
        val changes = mutableListOf<Pair<String, String?>>()
        val options = listOf(PluginControlOption("one", "预设一"), PluginControlOption("two", "预设二"))
        val controls = (1..18).map { PluginControl.Toggle("开关 $it", "toggle-$it", false) } +
            PluginControl.Choice("预设", "preset", options, "one", PluginChoiceStyle.SELECT)
        compose.setContent { MaterialTheme { PluginControlsDialog(PluginOutcome.Controls("样式", controls),
            { action, value -> changes += action to value }, {}) } }
        compose.onNodeWithContentDescription("预设").performScrollTo().performClick()
        compose.onNodeWithText("预设二").performClick()
        assertEquals("preset" to "two", changes.last())
        compose.onNodeWithText("完成").assertIsDisplayed()
    }
}
