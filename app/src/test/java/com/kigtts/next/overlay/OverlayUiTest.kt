package com.kigtts.next.overlay

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.plugins.PluginControlsDialog
import com.kigtts.plugin.api.*
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.platform.testTag
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class OverlayUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun radialEntrancesOnlyBecomeClickableAfterExpandAndCollapseHasOneMeaning() {
        var expanded by mutableStateOf(false); var opened: String? = null
        var mode by mutableStateOf(ThemeMode.LIGHT)
        MaterialSymbols.initialize(compose.activity)
        compose.setContent { KigttsTheme(ThemeSettings(mode = mode)) { Surface(Modifier.size(296.dp, 356.dp).testTag("overlay-preview")) {
            OverlayDial(OverlaySettings(), OverlayPlacement(0, 0, 888, 1068, 768f, 534f), expanded, false,
                { expanded = !expanded }, { _, _ -> }, {}, {}, { opened = it.id }, {})
        } } }
        compose.onNodeWithContentDescription("拍照").assertDoesNotExist()
        compose.onNodeWithContentDescription("展开快捷入口").performClick()
        java.io.File("build/previews").mkdirs()
        compose.onNodeWithTag("overlay-preview").captureRoboImage("build/previews/native-overlay-expanded.png")
        compose.runOnIdle { mode = ThemeMode.DARK }
        compose.onNodeWithTag("overlay-preview").captureRoboImage("build/previews/native-overlay-expanded-dark.png")
        compose.onNodeWithContentDescription("拍照").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("camera", opened) }
        compose.onNodeWithContentDescription("收起快捷入口").performClick()
        compose.onNodeWithContentDescription("展开快捷入口").assertExists()
    }
    @Test fun draggingAnEntranceScrollsTheDialWithoutOpeningIt() {
        var opened: String? = null
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.size(296.dp, 356.dp)) {
            OverlayDial(OverlaySettings(), OverlayPlacement(0, 0, 888, 1068, 768f, 534f), true, false,
                {}, { _, _ -> }, {}, {}, { opened = it.id }, {})
        } } }
        val icon = compose.onNodeWithContentDescription("拍照")
        val before = icon.fetchSemanticsNode().boundsInRoot
        icon.performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 320f), 350)
        }
        compose.waitForIdle()
        val after = compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { assertNull(opened); assertNotEquals(before, after) }
    }
    @Test fun labelVisibilitySwitchDoesNotRemoveEntranceIcons() {
        var labels by mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.size(296.dp, 356.dp)) {
            OverlayDial(OverlaySettings(showLabels = labels), OverlayPlacement(0, 0, 888, 1068, 768f, 534f), true, false,
                {}, { _, _ -> }, {}, {}, {}, {})
        } } }
        compose.onNodeWithText("拍照").assertExists()
        compose.runOnIdle { labels = false }
        compose.onNodeWithText("拍照").assertDoesNotExist()
        compose.onNodeWithContentDescription("拍照").assertIsEnabled()
        compose.runOnIdle { labels = true }
        compose.onNodeWithText("拍照").assertExists()
    }
    @Test fun holdingAnEntranceThenDraggingRequestsReorderInsteadOfOpening() {
        val moves = mutableListOf<Pair<String, Int>>()
        var opened: String? = null
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.size(296.dp, 356.dp)) {
            OverlayDial(OverlaySettings(), OverlayPlacement(0, 0, 888, 1068, 768f, 534f), true, false,
                {}, { _, _ -> }, {}, {}, { opened = it.id }, {}, reorder = { id, direction -> moves += id to direction; true })
        } } }
        compose.onNodeWithContentDescription("拍照").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 40f), 20)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 220f), 100)
            up()
        }
        compose.runOnIdle { assertNull(opened); assertTrue(moves.contains("camera" to 1)) }
    }
    @Test fun motionButtonsAndDialogActionsTickOnceAndHonorGlobalOff() {
        val ticks = mutableListOf<HapticFeedbackType>(); var enabled by mutableStateOf(true); var actions = 0
        val native = object : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { ticks += hapticFeedbackType } }
        compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides native) { KigttsTheme(ThemeSettings()) { ProvideButtonFeedback(enabled) {
            Column {
                MotionTextButton(feedbackClick { actions++ }) { Text("已包装") }
                MotionTextButton({ actions++ }) { Text("原始回调") }
                MotionTextButton(dialogFeedbackClick { actions++ }) { Text("弹窗回调") }
            }
        } } } }
        for (label in listOf("已包装", "原始回调", "弹窗回调")) compose.onNodeWithText(label).performClick()
        compose.runOnIdle { assertEquals(3, ticks.size); assertEquals(3, actions); enabled = false }
        compose.onNodeWithText("原始回调").performClick()
        compose.runOnIdle { assertEquals(3, ticks.size); assertEquals(4, actions) }
    }
    @Test fun pluginToggleAndSegmentedChoiceHaveFeedbackInsideADialog() {
        val ticks = mutableListOf<HapticFeedbackType>(); val changes = mutableListOf<String>()
        val native = object : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { ticks += hapticFeedbackType } }
        compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides native) { KigttsTheme(ThemeSettings()) { ProvideButtonFeedback(true) {
            PluginControlsDialog(PluginOutcome.Controls("设置", listOf(PluginControl.Toggle("灯珠", "toggle", false),
                PluginControl.Choice("形状", "shape", listOf(PluginControlOption("circle", "圆点"), PluginControlOption("square", "方点")), "circle"))),
                { action, _ -> changes += action }, {})
        } } } }
        compose.onNodeWithContentDescription("灯珠").performClick(); compose.onNodeWithText("方点").performClick()
        compose.runOnIdle { assertEquals(listOf("toggle", "shape"), changes); assertEquals(listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.VirtualKey), ticks) }
    }
}
