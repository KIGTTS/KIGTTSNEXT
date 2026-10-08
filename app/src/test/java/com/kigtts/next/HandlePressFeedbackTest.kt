package com.kigtts.next

import android.app.Application
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
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
class HandlePressFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val ticks = mutableListOf<HapticFeedbackType>()
    private val density get() = compose.activity.resources.displayMetrics.density
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "interaction", "quick-phrases").forEach {
            app.getSharedPreferences(it, 0).edit().clear().commit()
        }
        val native = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { ticks += hapticFeedbackType }
        }
        return AppViewModel(app).also { model ->
            compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides native) { KigttsApp(model) } }
        }
    }
    private fun step() { compose.mainClock.advanceTimeBy(320); compose.waitForIdle() }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")

    @Test fun allThreeHandlesReactToDownAndCancelWithoutMovingContent() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f) }
        fun pressAndCancel(touchTag: String, indicator: String, before: () -> Any) {
            val value = before()
            val normal = compose.onNodeWithTag(indicator).getUnclippedBoundsInRoot()
            ticks.clear(); compose.mainClock.autoAdvance = false
            compose.onNodeWithTag(touchTag).performTouchInput { down(center) }
            compose.runOnIdle { assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), ticks) }
            step()
            val pressed = compose.onNodeWithTag(indicator).getUnclippedBoundsInRoot()
            assertTrue((pressed.right - pressed.left).value > (normal.right - normal.left).value + 3f)
            assertTrue((pressed.bottom - pressed.top).value > (normal.bottom - normal.top).value + 1f)
            assertEquals(value, before())
            capture(indicator + "-pressed-before-drag")
            compose.onNodeWithTag(touchTag).performTouchInput { cancel() }
            compose.mainClock.advanceTimeBy(1200); compose.waitForIdle(); compose.mainClock.autoAdvance = true
            assertEquals(normal, compose.onNodeWithTag(indicator).getUnclippedBoundsInRoot())
            assertEquals(value, before())
            assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), ticks)
        }
        pressAndCancel("sheet-handle", "dock-main") { model.state.value.panelHeight }
        assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel)
        compose.runOnIdle { model.closePanel(); model.toggleListening() }
        pressAndCancel("top-panel-resize", "listening-drag-indicator") { model.state.value.captionHeight }
        val captionHeight = model.state.value.captionHeight
        ticks.clear()
        compose.onNodeWithTag("top-panel-resize").performTouchInput { down(center); moveBy(Offset(0f, 40f * density)); cancel() }
        compose.waitForIdle()
        assertEquals(captionHeight, model.state.value.captionHeight)
        assertFalse(ticks.contains(HapticFeedbackType.GestureEnd))
        compose.runOnIdle { model.text("abcdef中文"); model.editing(true) }
        pressAndCancel("cursor-track", "cursor-drag-indicator") { model.state.value.editorValue }
        compose.runOnIdle { model.haptics(false); ticks.clear() }
        compose.onNodeWithTag("cursor-track").performTouchInput { down(center); up() }
        assertTrue(ticks.isEmpty())
    }

    @Test fun cursorNeedsTwelveDpAndTicksOnlyWhenARealGraphemeMoveSucceeds() {
        val model = show()
        val text = "a👨‍👩‍👧‍👦中"
        compose.runOnIdle { model.text(text); model.editing(true) }
        compose.waitForIdle()
        val track = compose.onNodeWithTag("cursor-track")
        val slop = ViewConfiguration.get(compose.activity).scaledTouchSlop.toFloat()
        fun shift(dp: Float) { track.performTouchInput { moveBy(Offset(dp * density, 0f)) }; compose.waitForIdle() }
        fun moveTicks() = ticks.count { it == HapticFeedbackType.SegmentFrequentTick }
        compose.mainClock.autoAdvance = false
        track.performTouchInput { down(center); moveBy(Offset(-slop - density, 0f)) }
        shift(-10f)
        assertEquals(text.length, model.state.value.editorValue.selection.start)
        assertEquals(0, moveTicks())
        shift(-2f)
        assertEquals(text.length - 1, model.state.value.editorValue.selection.start)
        assertEquals(1, moveTicks())
        shift(-12f)
        assertEquals(1, model.state.value.editorValue.selection.start)
        assertEquals(2, moveTicks())
        shift(-12f)
        assertEquals(0, model.state.value.editorValue.selection.start)
        assertEquals(3, moveTicks())
        shift(-36f)
        assertEquals(0, model.state.value.editorValue.selection.start)
        assertEquals(3, moveTicks())
        shift(14f)
        assertEquals(1, model.state.value.editorValue.selection.start)
        assertEquals(4, moveTicks())
        assertEquals(1, ticks.count { it == HapticFeedbackType.GestureThresholdActivate })
        track.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        assertEquals(1, ticks.count { it == HapticFeedbackType.GestureEnd })
    }
}
