package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
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
class SheetGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { model -> compose.setContent { KigttsApp(model) } }
    }
    @Test fun buttonAndDragCollapseRestoreLastExpandedHeight() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f) }
        compose.onNodeWithContentDescription("收起快捷文本").performClick()
        compose.onNodeWithTag("dock-main").performClick()
        compose.runOnIdle { assertEquals(340f, model.state.value.panelHeight) }
        val density = compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("sheet-handle").performTouchInput { swipeDown(startY = centerY, endY = centerY + 300f * density, durationMillis = 800) }
        compose.runOnIdle { assertNull(model.state.value.panel); assertEquals(340f, model.state.value.lastExpandedPanelHeight) }
        compose.onNodeWithTag("dock-main").performClick()
        compose.runOnIdle {
            assertEquals(340f, model.state.value.panelHeight)
            assertEquals(340f, testAppModel(ApplicationProvider.getApplicationContext()).state.value.lastExpandedPanelHeight)
        }
    }
    @Test fun upwardDockDragSurvivesMorphAndPublishesOnlyOnRelease() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f); model.closePanel() }
        compose.onNodeWithTag("dock-main").assertIsDisplayed()
        val density = compose.activity.resources.displayMetrics.density
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("dock-main").performTouchInput { down(center); moveBy(Offset(0f, -18f * density)) }
        repeat(10) {
            compose.mainClock.advanceTimeBy(24); compose.waitForIdle()
            compose.onNodeWithTag("dock-main").performTouchInput { moveBy(Offset(0f, -12f * density)) }
        }
        compose.mainClock.advanceTimeBy(24); compose.waitForIdle()
        compose.runOnIdle { assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel); assertEquals(72f, model.state.value.panelHeight) }
        val height = compose.onNodeWithTag("sheet-handle").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue(height > 170f)
        compose.onRoot().captureRoboImage("build/previews/dock-swipe-mid.png")
        compose.onNodeWithTag("dock-main").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500); compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(340f, model.state.value.panelHeight); assertFalse(model.state.value.captionsVisible) }
        compose.onNodeWithTag("phrases-vertical").assertIsDisplayed()
    }
    @Test fun listeningResizeAlsoStaysLocalUntilRelease() {
        val model = show()
        compose.runOnIdle { model.toggleListening() }
        val initial = model.state.value.captionHeight
        val density = compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("top-panel-resize").performTouchInput { down(center); moveBy(Offset(0f, 40f * density)) }
        compose.runOnIdle { assertEquals(initial, model.state.value.captionHeight) }
        val current = compose.onNodeWithTag("top-panel-resize").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue(current > initial)
        compose.onNodeWithTag("top-panel-resize").performTouchInput { up() }
        compose.runOnIdle { assertTrue(model.state.value.captionHeight > initial) }
    }
}
