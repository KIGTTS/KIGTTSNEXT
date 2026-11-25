package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.ui.plugins.PluginControlsDialog
import com.kigtts.next.ui.plugins.PluginPageScreen
import androidx.compose.ui.geometry.Offset
import com.kigtts.plugin.api.*
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeMode
import com.kigtts.next.theme.ThemeSettings
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
    @Test fun speechGainUsesHalfTheTrackForZeroToOneAndCommitsOnce() {
        val changes = mutableListOf<Float>()
        val page = PluginPage("audio", "朗读", listOf(PageBlock.Slider(PluginControl.Slider(
            "音量", com.kigtts.next.audio.SPEECH_GAIN_ACTION, 0f, 0f, 10f))))
        compose.setContent { MaterialTheme { PluginPageScreen(page) { _, inputs -> changes += inputs.getValue("value").toFloat() } } }
        compose.onNodeWithContentDescription("音量").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(listOf(1f), changes) }
        compose.onNodeWithContentDescription("音量").performTouchInput { swipe(center, Offset(width - 4f, center.y), durationMillis = 300) }
        compose.runOnIdle { assertTrue(changes.last() in 9f..10f); assertEquals(2, changes.size) }
        compose.onNodeWithContentDescription("音量").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.runOnIdle { assertEquals(10f, changes.last(), .01f); assertEquals(3, changes.size) }
        compose.onNodeWithText("10×").assertIsDisplayed()
    }
    @Test fun ledChoicesKeepConnectedShapesAndSpacingInBothThemes() = ledStyleGroups()

    @Test @Config(qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
    fun ledChoicesKeepConnectedShapesAndSpacingInLandscape() = ledStyleGroups("landscape")

    private fun ledStyleGroups(orientation: String = "portrait") {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val changes = mutableListOf<Pair<String, String?>>()
        val request = PluginOutcome.Controls("LED 样式", listOf(
            PluginControl.Choice("灯珠形状", "shape", listOf(PluginControlOption("circle", "圆点"),
                PluginControlOption("square", "方点")), "circle"),
            PluginControl.Choice("滚动方向", "direction", listOf(PluginControlOption("left", "向左"),
                PluginControlOption("right", "向右")), "left"),
            PluginControl.Choice("文本对齐", "align", listOf(PluginControlOption("start", "居左"),
                PluginControlOption("center", "居中"), PluginControlOption("end", "居右")), "center")))
        compose.setContent { KigttsTheme(ThemeSettings(mode = mode.value)) {
            PluginControlsDialog(request, { action, value -> changes += action to value }, {})
        } }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { theme ->
            compose.runOnIdle { mode.value = theme }
            compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
            val middle = compose.onNodeWithContentDescription("文本对齐：居中").performScrollTo()
            val first = compose.onNodeWithContentDescription("文本对齐：居左").getUnclippedBoundsInRoot()
            val center = middle.getUnclippedBoundsInRoot()
            val last = compose.onNodeWithContentDescription("文本对齐：居右").getUnclippedBoundsInRoot()
            assertTrue("Connected buttons keep a narrow visible gap", (center.left - first.right).value in 1f..5f)
            assertEquals((center.left - first.right).value, (last.left - center.right).value, 1f)
            compose.onNodeWithTag("plugin-controls").captureRoboImage("build/previews/led-button-groups-$orientation-${theme.name.lowercase()}.png")
        }
        compose.onNodeWithContentDescription("文本对齐：居右").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("文本对齐：居中").assertIsNotSelected()
        compose.runOnIdle { assertEquals(listOf("align" to "end"), changes) }
    }

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
    @Test fun inlineSliderStaysInteractiveDuringDraggingAndSavesOnlyWhenReleased() {
        val changes = mutableListOf<Pair<String, Map<String, String>>>()
        val page = PluginPage("audio", "语音合成", listOf(PageBlock.Group("朗读", listOf(
            PageBlock.Slider(PluginControl.Slider("语速", "rate", 1f, .25f, 4f, suffix = "×"))))))
        compose.setContent { MaterialTheme { PluginPageScreen(page) { action, inputs -> changes += action to inputs } } }
        compose.onNodeWithContentDescription("语速").performTouchInput {
            down(center); moveBy(Offset(40f, 0f))
        }
        compose.runOnIdle { assertTrue("Dragging must not trigger worker busy refreshes", changes.isEmpty()) }
        compose.onNodeWithContentDescription("语速").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, changes.size); assertEquals("rate", changes.single().first)
            assertTrue(changes.single().second.getValue("value").toFloat() in .25f..4f)
        }
    }

    @Test fun ledDialogSliderOnlyCommitsOnceWhenReleased() {
        val changes = mutableListOf<Pair<String, String?>>()
        val request = PluginOutcome.Controls("LED 样式",
            listOf(PluginControl.Slider("灯珠大小", "size", 58f, 10f, 100f, suffix = "%")))
        compose.setContent {
            MaterialTheme {
                PluginControlsDialog(request, { action, value -> changes += action to value }, {},
                    commitSlidersOnRelease = true)
            }
        }
        val slider = compose.onNodeWithContentDescription("灯珠大小")
        slider.performTouchInput { down(center); moveBy(Offset(40f, 0f)) }
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
        slider.performTouchInput { up() }
        compose.runOnIdle { assertEquals("size", changes.single().first) }
    }
}
