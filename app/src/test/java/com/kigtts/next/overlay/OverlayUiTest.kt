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
import androidx.compose.ui.draw.clipToBounds
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
    @Test fun fifthEntranceAtTheStartIsNotCoveredByTheInvisibleSixthLabel() {
        var opened: String? = null
        var right by mutableStateOf(true)
        val entries = (1..7).map { OverlayShortcut("entry-$it", "快捷入口名称 $it", "extension") }
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.size(296.dp, 356.dp)) {
            OverlayDial(OverlaySettings(rightEdge = right, shortcuts = entries),
                OverlayPlacement(0, 0, 888, 1068, if (right) 768f else 120f, 534f), true, false,
                {}, { _, _ -> }, {}, {}, { opened = it.id }, {})
        } } }
        for (edge in listOf(true, false)) {
            compose.runOnIdle { right = edge; opened = null }
            compose.onNodeWithContentDescription(entries[4].title).performTouchInput { click() }
            compose.runOnIdle { assertEquals("Fifth entrance on ${if (edge) "right" else "left"} edge", entries[4].id, opened) }
        }
    }
    @Test fun fifthSubtitleBoardRemainsClickableWhileTheFirstPageBouncesAtItsTopEdge() {
        var opened: String? = null
        val defaults = defaultOverlayShortcuts()
        val shortcuts = defaults.drop(1) + OverlayShortcut("other", "其它入口", "extension") + defaults.first() +
            (1..3).map { OverlayShortcut("extra-$it", "额外入口$it", "extension") }
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.size(296.dp, 356.dp).testTag("dial")) {
            OverlayDial(OverlaySettings(shortcuts = shortcuts), OverlayPlacement(0, 0, 888, 1068, 768f, 534f), true, false,
                {}, { _, _ -> }, {}, {}, { opened = it.id }, {})
        } } }
        val dial = compose.onNodeWithTag("dial")
        dial.performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, -600f), 500) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        dial.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, 1300f), 500) }
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        // The fifth entry is still on screen during resisted overscroll, rather than disabled at alpha .4.
        compose.onNodeWithContentDescription("字幕板").assertIsEnabled()
        dial.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(16)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("字幕板").performTouchInput { click() }
        compose.runOnIdle { assertEquals("home", opened) }
        compose.mainClock.autoAdvance = true
    }
    @Test fun cornerAnchorMovesContinuouslyAndFinishesReturningInTheSameTransition() {
        var expanded by mutableStateOf(true)
        var center = androidx.compose.ui.geometry.Offset.Zero
        var collapsed = 0
        val dock = androidx.compose.ui.geometry.Offset(768f, 120f)
        compose.mainClock.autoAdvance = false
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.size(296.dp, 356.dp)) {
            OverlayDial(OverlaySettings(), OverlayPlacement(0, 0, 888, 1068, 768f, 534f), expanded, false,
                {}, { _, _ -> }, {}, {}, {}, {}, { collapsed++ }, initialCenter = dock, collapsedCenter = dock,
                centerChanged = { center = it })
        } } }
        compose.waitForIdle()
        assertEquals(dock.y, center.y, 1f)
        compose.mainClock.advanceTimeBy(96); compose.waitForIdle()
        assertTrue("Opening midpoint: $center", center.y > dock.y && center.y < 534f)
        compose.mainClock.advanceTimeBy(240); compose.waitForIdle()
        assertEquals(534f, center.y, .01f)
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(112); compose.waitForIdle()
        assertTrue("Closing midpoint: $center, completion callbacks: $collapsed", center.y > dock.y && center.y < 534f)
        assertEquals(0, collapsed)
        compose.mainClock.advanceTimeBy(240); compose.waitForIdle()
        assertEquals(dock.y, center.y, .01f); assertEquals(1, collapsed)
        compose.mainClock.autoAdvance = true
    }
    @Test fun retractedFabKeepsASquareDrawingInsideTheHalfWidthTouchWindow() {
        var right by mutableStateOf(true)
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.size(28.dp, 56.dp).clipToBounds().testTag("half-fab-window")) {
                OverlayDial(OverlaySettings(rightEdge = right), OverlayPlacement(0, 0, 84, 168, if (right) 84f else 0f, 84f), false, true,
                    {}, { _, _ -> }, {}, {}, {}, {}, edgeRetraction = { 1f })
            }
        } }
        for (edge in listOf(true, false)) {
            compose.runOnIdle { right = edge }
            val bounds = compose.onNodeWithContentDescription("展开快捷入口").getUnclippedBoundsInRoot()
            assertEquals("A clipped FAB must retain its full square, not stretch into an ellipse", 56f, (bounds.right - bounds.left).value, .5f)
            assertEquals(56f, (bounds.bottom - bounds.top).value, .5f)
            compose.onNodeWithTag("half-fab-window").captureRoboImage("build/previews/overlay-half-${if (edge) "right" else "left"}.png")
        }
    }
    @Test fun lowestVisibleEntranceAcceptsTouchOnRightAndAfterScrollingToTheEnd() = checkLowestEntrance(true)
    @Test fun lowestVisibleEntranceAcceptsTouchOnLeftAndAfterScrollingToTheEnd() = checkLowestEntrance(false)
    private fun checkLowestEntrance(right: Boolean) {
        var opened: String? = null
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.size(296.dp, 356.dp)) {
            OverlayDial(OverlaySettings(rightEdge = right, shortcuts = defaultOverlayShortcuts() +
                (1..4).map { OverlayShortcut("extra-$it", "额外入口$it", "extension") }),
                OverlayPlacement(0, 0, 888, 1068, if (right) 768f else 120f, 534f), true, false,
                {}, { _, _ -> }, {}, {}, { opened = it.id }, {})
        } } }
        compose.waitForIdle()
        val last = defaultOverlayShortcuts().last()
        compose.onNodeWithContentDescription(last.title).performTouchInput { click() }
        compose.runOnIdle { assertEquals(last.id, opened); opened = null }
        compose.onNodeWithContentDescription("拍照").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, -600f), 700) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("额外入口4").performTouchInput { click() }
        compose.runOnIdle { assertEquals("extra-4", opened) }
    }
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
            OverlayDial(OverlaySettings(shortcuts = defaultOverlayShortcuts() +
                (1..3).map { OverlayShortcut("extra-$it", "额外入口$it", "extension") }), OverlayPlacement(0, 0, 888, 1068, 768f, 534f), true, false,
                {}, { _, _ -> }, {}, {}, { opened = it.id }, {})
        } } }
        val icon = compose.onNodeWithContentDescription("拍照")
        val before = icon.fetchSemanticsNode().boundsInRoot
        icon.performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, -180f), 350)
        }
        compose.waitForIdle()
        val after = compose.onNodeWithContentDescription("拍照").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { assertNull(opened); assertNotEquals(before, after) }
    }
    @Test fun draggingTheExpandedFabDoesNotMoveOrCollapseTheBall() {
        var expanded by mutableStateOf(true)
        var total = androidx.compose.ui.geometry.Offset.Zero
        var dropped = 0
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.size(296.dp, 356.dp)) {
            OverlayDial(OverlaySettings(), OverlayPlacement(0, 0, 888, 1068, 768f, 534f), expanded, false,
                {}, { x, y -> expanded = false; total += androidx.compose.ui.geometry.Offset(x, y) }, { dropped++ }, {}, {}, {})
        } } }
        compose.onNodeWithContentDescription("收起快捷入口").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(-80f, 110f), 300)
        }
        compose.runOnIdle {
            assertTrue(expanded); assertEquals(0f, total.x, .01f); assertEquals(0f, total.y, .01f); assertEquals(0, dropped)
        }
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
    @Test fun offCenterHoldKeepsGrabPointAndFollowsFingerThroughReorder() {
        var settings by mutableStateOf(OverlaySettings())
        compose.setContent { KigttsTheme(ThemeSettings()) { Surface(Modifier.size(296.dp, 356.dp)) {
            OverlayDial(settings, OverlayPlacement(0, 0, 888, 1068, 768f, 534f), true, false,
                {}, { _, _ -> }, {}, {}, {}, {}, reorder = { id, direction ->
                    val list = settings.shortcuts.toMutableList()
                    val from = list.indexOfFirst { it.id == id }; val to = from + direction
                    if (to in list.indices) {
                        list.add(to, list.removeAt(from)); settings = settings.copy(shortcuts = list); true
                    } else false
                })
        } } }
        compose.waitForIdle()
        val icon = compose.onNodeWithContentDescription("拍照")
        val before = icon.fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance = false
        icon.performTouchInput { down(androidx.compose.ui.geometry.Offset(width * .75f, height * .25f)) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
        val held = icon.fetchSemanticsNode().boundsInRoot.center
        assertEquals(before.x, held.x, 1f); assertEquals(before.y, held.y, 1f)
        val travel = androidx.compose.ui.geometry.Offset(25f, 180f)
        icon.performTouchInput { moveBy(travel, 100) }
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        val moved = icon.fetchSemanticsNode().boundsInRoot.center
        assertEquals(before.x + travel.x, moved.x, 2f); assertEquals(before.y + travel.y, moved.y, 2f)
        compose.runOnIdle { assertEquals(2, settings.shortcuts.indexOfFirst { it.id == "camera" }) }
        icon.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
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
