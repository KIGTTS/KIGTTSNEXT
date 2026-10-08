package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.theme.ThemeMode
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.UiSlot
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
class PanelInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val ticks = mutableListOf<HapticFeedbackType>()
    private lateinit var drawer: DrawerNavigation
    private val density get() = compose.activity.resources.displayMetrics.density
    private fun show(plugins: Int = 0): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "interaction", "quick-phrases").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        app.getSharedPreferences("ui-debug", 0).edit().putInt(UiSlot.QUICK_PANEL_START.name, plugins).putInt(UiSlot.QUICK_PANEL_END.name, plugins).commit()
        val native = object : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { ticks += hapticFeedbackType } }
        return AppViewModel(app).also { model ->
            compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides native) { drawer = rememberDrawerNavigation(); KigttsApp(model, drawer) } }
        }
    }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")
    @Test fun inputCursorStripKeepsRelativeMovementWithElasticAndHapticFeedback() {
        val model = show()
        compose.runOnIdle { model.text("abcdef中文"); model.editing(true) }
        val initial = compose.onNodeWithTag("cursor-drag-indicator").getUnclippedBoundsInRoot()
        val selection = model.state.value.editorValue.selection.start
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("cursor-track").performTouchInput { down(center); moveBy(Offset(-36f * density, 0f)) }
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        val moving = compose.onNodeWithTag("cursor-drag-indicator").getUnclippedBoundsInRoot()
        assertTrue((moving.right - moving.left).value > (initial.right - initial.left).value + 3f)
        assertTrue(model.state.value.editorValue.selection.start < selection)
        assertTrue(ticks.contains(HapticFeedbackType.GestureThresholdActivate))
        capture("cursor-strip-drag-feedback")
        compose.onNodeWithTag("cursor-track").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("cursor-track").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
        assertTrue(ticks.contains(HapticFeedbackType.GestureEnd))
    }
    @Test fun upwardDragSurvivesBothPluginSlotsAndLiveChangesUntilRelease() {
        val model = show(3)
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f); model.closePanel() }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("dock-main").performTouchInput { down(center); moveBy(Offset(0f, -22f * density)) }
        repeat(12) { step ->
            compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
            if (step == 4) compose.runOnIdle { model.invoke("debug.ui", "QUICK_PANEL_START/add") }
            compose.onNodeWithTag("dock-main").performTouchInput { moveBy(Offset(0f, -14f * density)) }
        }
        val current = compose.onNodeWithTag("sheet-handle").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue(current > 240f)
        assertEquals(72f, model.state.value.panelHeight, .1f)
        capture("dock-plugin-drag-mid")
        compose.onNodeWithTag("dock-main").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500); compose.mainClock.autoAdvance = true
        assertEquals(340f, model.state.value.panelHeight, .1f)
        compose.onNodeWithTag("QUICK_PANEL_START-more").assertIsDisplayed()
        compose.onNodeWithTag("QUICK_PANEL_END-more").assertIsDisplayed()
        assertTrue(ticks.contains(HapticFeedbackType.GestureThresholdActivate))
        assertTrue(ticks.contains(HapticFeedbackType.GestureEnd))
    }
    @Test fun handlesGrowWhileDraggingAndFeedbackCanBeDisabled() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f) }
        val initial = compose.onNodeWithTag("dock-main").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("sheet-handle").performTouchInput { down(center); moveBy(Offset(0f, -58f * density)) }
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        val active = compose.onNodeWithTag("dock-main").getUnclippedBoundsInRoot()
        assertTrue((active.right - active.left).value > (initial.right - initial.left).value + 5f)
        assertTrue((active.bottom - active.top).value > (initial.bottom - initial.top).value + 1f)
        capture("sheet-handle-active")
        compose.onNodeWithTag("sheet-handle").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1200); compose.mainClock.autoAdvance = true
        assertTrue(ticks.contains(HapticFeedbackType.SegmentFrequentTick))
        compose.runOnIdle { model.closePanel(); model.toggleListening() }
        val normal = compose.onNodeWithTag("listening-drag-indicator").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("top-panel-resize").performTouchInput { down(center); moveBy(Offset(0f, 50f * density)) }
        // The resized panel measures its AnimatedVisibility subcomposition between real frames.
        repeat(8) { compose.mainClock.advanceTimeBy(50); compose.waitForIdle() }
        val resized = compose.onNodeWithTag("listening-drag-indicator").getUnclippedBoundsInRoot()
        assertTrue("normal=" + normal + ", resized=" + resized,
            (resized.bottom - resized.top).value > (normal.bottom - normal.top).value + 1f)
        capture("listening-handle-active")
        compose.onNodeWithTag("top-panel-resize").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { model.haptics(false); ticks.clear() }
        compose.onNodeWithTag("top-panel-resize").performTouchInput { swipeDown() }
        assertTrue(ticks.isEmpty())
    }
    @Test fun backgroundFadesSeparatelyAndLivePluginButtonsAppearAndDisappear() {
        val model = show()
        compose.runOnIdle { model.theme(model.state.value.theme.copy(mode = ThemeMode.LIGHT)) }
        fun pixel(name: String): Int {
            val path = "build/previews/" + name + ".png"
            compose.onNodeWithTag("sheet-background").captureRoboImage(path)
            return BitmapFactory.decodeFile(path).let { it.getPixel(it.width / 2, (2f * density).toInt()) }
        }
        val before = pixel("sheet-background-closed")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
        val mid = pixel("sheet-background-open-mid")
        compose.mainClock.advanceTimeBy(1400); compose.waitForIdle()
        val after = pixel("sheet-background-open")
        assertNotEquals(before, mid); assertNotEquals(after, mid)
        compose.runOnIdle { model.invoke("debug.ui", "QUICK_PANEL_START/add") }
        compose.mainClock.advanceTimeBy(96); compose.waitForIdle(); capture("plugin-button-appear-mid")
        compose.mainClock.advanceTimeBy(1400); compose.waitForIdle()
        compose.onNodeWithTag("debug.ui/QUICK_PANEL_START/0").assertIsDisplayed()
        compose.runOnIdle { model.invoke("debug.ui", "QUICK_PANEL_START/remove") }
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle(); capture("plugin-button-disappear-mid")
        compose.mainClock.advanceTimeBy(1400); compose.waitForIdle()
        compose.onNodeWithTag("debug.ui/QUICK_PANEL_START/0").assertDoesNotExist()
        compose.runOnIdle { model.closePanel() }
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
        val closing = pixel("sheet-background-close-mid")
        assertNotEquals(after, closing); assertNotEquals(before, closing)
        compose.mainClock.advanceTimeBy(1400); compose.mainClock.autoAdvance = true
        assertEquals(before, pixel("sheet-background-closed-restored"))
    }
    @Test fun rightSwipeAndBlankTapCloseDrawerAndClosedDrawerCannotSwipeOpen() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        fun closed() { compose.waitForIdle(); assertFalse(drawer.active); assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel) }
        compose.onRoot().performTouchInput { swipeLeft() }; closed()
        compose.onNodeWithContentDescription("打开抽屉菜单").performClick()
        compose.onNodeWithTag("app-drawer").performTouchInput { swipeRight(startX = width * .15f, endX = width * .95f, durationMillis = 350) }
        closed()
        compose.onNodeWithContentDescription("打开抽屉菜单").performClick()
        compose.onRoot().performTouchInput { click(Offset(6f * density, height * .65f)) }
        closed()
        capture("drawer-blank-dismissed")
    }
    @Test fun swipeDuringOpeningDoesNotLeaveTheDrawerActiveOrDismissThePanel() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { drawer.open() }
        compose.mainClock.advanceTimeBy(96); compose.waitForIdle()
        compose.onNodeWithTag("app-drawer").performTouchInput { swipeRight(startX = 6f * density, endX = 170f * density, durationMillis = 250) }
        compose.mainClock.advanceTimeBy(1600); compose.mainClock.autoAdvance = true
        assertFalse(drawer.active)
        assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel)
    }
}
