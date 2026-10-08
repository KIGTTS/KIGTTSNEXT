package com.kigtts.next

import android.app.Application
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
class AdaptiveUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val longText = "长字幕需要在有限的空间完整显示，同时保留用户选择的字号。".repeat(22)

    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("ui-debug", 0).edit().clear().commit()
        app.getSharedPreferences("appearance", 0).edit().clear().commit()
        app.getSharedPreferences("home-panel", 0).edit().clear().commit()
        app.getSharedPreferences("voice-input", 0).edit().clear().commit()
        val model = AppViewModel(app)
        compose.setContent { KigttsApp(model) }
        return model
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")
    private fun width(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.right - it.left).value }
    private fun height(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
    private fun textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("speech-editor").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        return results.single()
    }
    private fun assertFits(text: String): TextLayoutResult {
        val layout = textLayout()
        assertEquals(text, layout.layoutInput.text.text)
        assertFalse("Text must not overflow the available region", layout.hasVisualOverflow)
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        return layout
    }

    @Test fun longSubtitleAdaptsToPanelHeightAndRestoresPreferredSize() {
        val model = show()
        compose.runOnIdle { model.setSubtitleSizePercent(6400f / 393f); model.text("你好") }
        val preferredGlyphHeight = assertFits("你好").getBoundingBox(0).height
        compose.runOnIdle { model.text(longText) }
        capture("subtitle-auto-fit")
        val spaciousLayout = assertFits(longText)
        val spacious = spaciousLayout.getBoundingBox(0).height
        assertTrue("Rendered glyph should shrink", spacious < preferredGlyphHeight)
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f); model.toggleListening() }
        val compact = assertFits(longText).getBoundingBox(0).height
        assertTrue(compact <= spacious)
        assertTrue(assertFits(longText).layoutInput.style.fontSize.value >= 24f)
        capture("subtitle-auto-fit-panels")
        compose.runOnIdle { model.text("你好"); assertEquals(6400f / 393f, model.state.value.subtitleSizePercent) }
        assertEquals(preferredGlyphHeight, assertFits("你好").getBoundingBox(0).height, .5f)
    }

    @Test fun longInputFitsKeyboardSpaceWithoutChangingSelectionOrPreferredSize() {
        val model = show()
        compose.runOnIdle { model.setSubtitleSizePercent(6400f / 393f); model.text(longText); model.editing(true) }
        dispatchInsets(300)
        val fitting = assertFits(longText).layoutInput.style.fontSize.value
        assertTrue(fitting < 64f)
        assertTrue(fitting >= 24f)
        compose.runOnIdle { assertEquals(longText.length, model.state.value.editorValue.selection.start); assertEquals(6400f / 393f, model.state.value.subtitleSizePercent) }
        capture("subtitle-auto-fit-keyboard")
        compose.runOnIdle { model.text("短字幕") }
        assertEquals(64f, assertFits("短字幕").layoutInput.style.fontSize.value, .25f)
        compose.onNodeWithTag("speech-editor").performTextInput("输入")
        compose.runOnIdle { assertEquals("短字幕输入", model.state.value.editorText) }
    }

    private fun dispatchInsets(imeDp: Int) {
        compose.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            val density = compose.activity.resources.displayMetrics.density
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (24 * density).toInt(), 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (24 * density).toInt()))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (imeDp * density).toInt()))
                .setVisible(WindowInsetsCompat.Type.ime(), imeDp > 0).build()
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets)
        }
        compose.waitForIdle()
    }

    @Test fun imeIsCountedOnceAndFooterStaysImmediatelyAboveKeyboard() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f); model.toggleListening(); model.editing(true) }
        dispatchInsets(300)
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val editor = compose.onNodeWithTag("editor-area").getUnclippedBoundsInRoot()
        val footer = compose.onNodeWithTag("editor-footer").getUnclippedBoundsInRoot()
        assertEquals("No duplicate IME padding", 300f, (root.bottom - editor.bottom).value, 1f)
        assertEquals(24f, editor.top.value, 1f)
        assertEquals(8f, (editor.bottom - footer.bottom).value, 1f)
        capture("keyboard-insets-once")
        dispatchInsets(0)
        compose.runOnIdle {
            assertFalse(model.state.value.editing)
            assertTrue(model.state.value.captionsVisible)
            assertEquals(340f, model.state.value.panelHeight)
        }
        compose.onNodeWithTag("captions").assertIsDisplayed()
        compose.onNodeWithTag("home-sheet").assertIsDisplayed()
    }

    @Test fun inputCollapsesChromeOverIntermediateFramesAndRestoresIt() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.toggleListening() }
        val header = height("header-transition")
        val listening = height("listening-transition")
        val editor = height("editor-area")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.editing(true) }
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        assertTrue(height("header-transition") in .1f..(header - .1f))
        assertTrue(height("listening-transition") in .1f..(listening - .1f))
        assertTrue(height("editor-area") > editor)
        compose.onNodeWithTag("home-sheet").assertExists()
        capture("input-collapse-mid-frame")
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        compose.onNodeWithTag("captions").assertDoesNotExist()
        compose.onNodeWithTag("home-sheet").assertDoesNotExist()
        compose.onNodeWithContentDescription("KIGTTS 横版 Logo").assertDoesNotExist()
        compose.runOnIdle { model.editing(false) }
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        assertTrue(height("header-transition") in .1f..(header - .1f))
        capture("input-restore-mid-frame")
        compose.mainClock.advanceTimeBy(1200)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("captions").assertIsDisplayed()
        assertEquals(header, height("header-transition"), .5f)
    }

    private fun holdGrowth(tag: String, held: () -> Unit = {}): Float {
        val before = width(tag)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(tag).performTouchInput { down(center) }
        // BoxWithConstraints subcomposition needs actual measure passes between clock frames.
        repeat(8) { compose.mainClock.advanceTimeBy(32); compose.waitForIdle() }
        val growth = width(tag) - before
        held()
        compose.onNodeWithTag(tag).performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1200)
        compose.mainClock.autoAdvance = true
        return growth
    }

    @Test fun longAndShortButtonsBorrowComparableWidth() {
        show()
        val shortGrowth = holdGrowth("editor-flip")
        val longGrowth = holdGrowth("panel.listening/toggle")
        assertTrue("Short growth: " + shortGrowth + ", long growth: " + longGrowth, shortGrowth > 3f)
        assertEquals(shortGrowth, longGrowth, 1f)
    }

    @Test fun collapsedDockIsLargerAndLongButtonDoesNotCrushRoundButtons() {
        val model = show()
        assertEquals(48f, height("dock-start"), .5f)
        assertEquals(48f, height("dock-main"), .5f)
        val side = width("dock-start")
        val longGrowth = holdGrowth("dock-main") {
            assertTrue("Round neighbor must retain its usable size", width("dock-start") >= 42f)
            capture("dock-button-held-balanced")
        }
        assertTrue(longGrowth in 3f..7f)
        assertEquals(side, width("dock-start"), .5f)
        capture("home-larger-dock")
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        assertEquals(32f, height("dock-start"), .5f)
        assertEquals(5f, height("dock-main"), .5f)
        capture("quick-panel-small-controls")
    }
}
