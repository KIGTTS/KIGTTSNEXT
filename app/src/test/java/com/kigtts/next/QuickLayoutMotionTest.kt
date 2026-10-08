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
class QuickLayoutMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun layoutSwitchesAnimateBothWaysAndRetainHorizontalScroll() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        val model = AppViewModel(app)
        compose.setContent { KigttsApp(model) }
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        compose.onNodeWithTag("phrases-horizontal").performTouchInput { swipeLeft() }
        val scrolled = compose.onNodeWithTag("phrases-horizontal").fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue(scrolled > 0f)
        fun waitForLayout(tag: String) {
            repeat(60) {
                compose.mainClock.advanceTimeBy(16); compose.waitForIdle()
                if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
                    compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
                    return
                }
            }
            fail("Layout did not appear: " + tag)
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.panelHeight(340f) }
        waitForLayout("phrases-vertical")
        compose.onNodeWithTag("phrases-vertical").assertExists()
        compose.onRoot().captureRoboImage("build/previews/quick-layout-to-grid-mid.png")
        compose.mainClock.advanceTimeBy(1200); compose.waitForIdle()
        compose.onNodeWithTag("phrases-horizontal").assertDoesNotExist()
        compose.onNodeWithTag("phrases-vertical").assertIsDisplayed()
        compose.runOnIdle { model.panelHeight(184f) }
        waitForLayout("phrases-horizontal")
        compose.onRoot().captureRoboImage("build/previews/quick-layout-to-row-mid.png")
        compose.mainClock.advanceTimeBy(1200); compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("phrases-vertical").assertDoesNotExist()
        val restored = compose.onNodeWithTag("phrases-horizontal").fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertEquals(scrolled, restored, .05f)
        compose.onNodeWithTag("category-tabs").captureRoboImage("build/previews/category-tab-rounded-ends.png")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("category-常用").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(320); compose.waitForIdle()
        compose.onNodeWithTag("category-tabs").captureRoboImage("build/previews/category-tab-pressed-corners.png")
        assertFalse(java.io.File("build/previews/category-tab-rounded-ends.png").readBytes()
            .contentEquals(java.io.File("build/previews/category-tab-pressed-corners.png").readBytes()))
        compose.onNodeWithTag("category-常用").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1000); compose.mainClock.autoAdvance = true
    }

    @Test fun verticalSwipesScrollContentWithoutResizingThePanel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        val model = AppViewModel(app)
        compose.setContent { KigttsApp(model) }
        compose.runOnIdle {
            repeat(24) { model.addPhrase("滚动条目 " + it, "常用") }
            model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f)
        }
        compose.onNodeWithTag("phrases-vertical").performTouchInput { swipeUp() }
        val scrolled = compose.onNodeWithTag("phrases-vertical").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(scrolled > 0f)
        assertEquals(340f, model.state.value.panelHeight, .1f)
        repeat(3) { compose.onNodeWithTag("phrases-vertical").performTouchInput { swipeDown() } }
        assertEquals(340f, model.state.value.panelHeight, .1f)
        assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel)
        compose.onNodeWithTag("phrases-vertical").assertIsDisplayed()
    }
}
