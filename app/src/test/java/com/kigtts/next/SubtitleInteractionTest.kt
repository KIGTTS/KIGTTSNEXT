package com.kigtts.next

import android.app.Application
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import android.graphics.BitmapFactory
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.*
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
class SubtitleInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { model -> compose.setContent { KigttsApp(model) } }
    }
    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")
    private fun pixels(path: String): IntArray {
        val image = BitmapFactory.decodeFile(path)
        return IntArray(image.width * image.height).also { image.getPixels(it, 0, image.width, 0, 0, image.width, image.height); image.recycle() }
    }
    private fun ime(height: Int) {
        compose.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            val density = compose.activity.resources.displayMetrics.density
            val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (height * density).toInt()))
                .setVisible(WindowInsetsCompat.Type.ime(), height > 0).build()
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets)
        }
        compose.waitForIdle()
    }
    @Test fun textMenuKeepsEditorFocusedAndDoesNotExitInput() {
        val model = show()
        compose.onNodeWithTag("subtitle-content").performClick()
        ime(300)
        compose.onNodeWithTag("speech-editor").assertIsFocused().performTextInput("继续输入")
        compose.onNodeWithTag("editor-menu").performClick()
        compose.onNodeWithText("打开粗体").assertExists()
        compose.onNodeWithTag("speech-editor").assertIsFocused()
        assertTrue(model.state.value.editing)
        back()
        compose.onNodeWithTag("speech-editor").assertIsFocused()
        assertTrue(model.state.value.editing)
        compose.onRoot().captureRoboImage("build/previews/editor-menu-keeps-input.png")
    }

    @Test fun blankCardEdgesOpenInputWhileButtonsRemainIndependent() {
        val model = show()
        repeat(2) { side ->
            compose.onNodeWithTag("subtitle-content").performTouchInput {
                click(Offset(if (side == 0) 3f else width - 3f, height * .2f))
            }
            compose.runOnIdle { assertTrue(model.state.value.editing); assertFalse(model.state.value.forcedFullscreen) }
            back()
        }
        compose.onNodeWithTag("editor-clear").performClick()
        compose.runOnIdle { assertFalse(model.state.value.editing); assertFalse(model.state.value.forcedFullscreen); assertEquals("", model.state.value.editorText) }
        compose.onNodeWithTag("editor-flip").performClick()
        compose.runOnIdle { assertFalse(model.state.value.editing); assertTrue(model.state.value.subtitleFlipped) }
        compose.onNodeWithTag("editor-font-size").performClick()
        compose.runOnIdle { assertFalse(model.state.value.editing); assertFalse(model.state.value.forcedFullscreen) }
    }

    @Test fun alignmentPickerDoesNotEndEditingWhenItsWindowTemporarilyHidesTheKeyboard() {
        val model = show()
        compose.onNodeWithTag("subtitle-content").performClick(); ime(300)
        compose.onNodeWithTag("speech-editor").performTextInput("保留输入")
        compose.onNodeWithTag("editor-menu").performClick()
        compose.onNodeWithTag("subtitle-alignment").performClick(); ime(0)
        compose.runOnIdle { assertTrue(model.state.value.editing) }
        compose.onNodeWithTag("subtitle-align-top_left").performClick(); compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(model.state.value.editing); assertEquals("保留输入", model.state.value.editorText)
            assertEquals(SubtitleAlignment.TOP_LEFT, model.state.value.subtitleAppearance.alignment)
        }
    }

    @Test fun heldFullscreenSurvivesImeDismissalAndPredictiveCancellationThenRestoresPanels() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f); model.toggleListening() }
        compose.onNodeWithTag("subtitle-content").performTouchInput { longClick(Offset(3f, height * .2f)) }
        compose.runOnIdle { assertTrue(model.state.value.forcedFullscreen); assertFalse(model.state.value.editing) }
        compose.onNodeWithTag("captions").assertDoesNotExist()
        compose.onNodeWithTag("home-sheet").assertDoesNotExist()
        compose.onNodeWithContentDescription("KIGTTS 横版 Logo").assertDoesNotExist()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val editor = compose.onNodeWithTag("editor-area").getUnclippedBoundsInRoot()
        assertEquals(root.bottom - root.top, editor.bottom - editor.top)
        capture("subtitle-forced-fullscreen")
        compose.onNodeWithTag("speech-editor").performClick()
        compose.onNodeWithTag("speech-editor").performTextInput("全屏输入")
        ime(260); ime(0)
        compose.runOnIdle { assertFalse(model.state.value.editing); assertTrue(model.state.value.forcedFullscreen) }
        compose.onNodeWithTag("home-sheet").assertDoesNotExist()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, .65f, BackEventCompat.EDGE_LEFT)) }
        capture("subtitle-fullscreen-predictive")
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.runOnIdle { assertTrue(model.state.value.forcedFullscreen) }
        back()
        compose.runOnIdle { assertFalse(model.state.value.forcedFullscreen); assertEquals(340f, model.state.value.panelHeight); assertTrue(model.state.value.captionsVisible) }
        compose.onNodeWithTag("captions").assertIsDisplayed()
        compose.onNodeWithTag("phrases-vertical").assertIsDisplayed()
    }

    @Test fun sendingUpdatingAndClearingSubtitleHaveVisibleIntermediateFrames() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT) }
        compose.waitForIdle()
        val phrase = model.phrases.value.first().text
        fun verifyFrame(name: String, change: () -> Unit) {
            compose.mainClock.autoAdvance = false
            change()
            compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
            val middlePath = "build/previews/" + name + "-mid.png"
            compose.onNodeWithTag("subtitle-content").captureRoboImage(middlePath)
            val middle = pixels(middlePath)
            compose.mainClock.advanceTimeBy(900); compose.waitForIdle()
            val endPath = "build/previews/" + name + "-end.png"
            compose.onNodeWithTag("subtitle-content").captureRoboImage(endPath)
            val end = pixels(endPath)
            assertFalse("Subtitle must visibly transition", middle.contentEquals(end))
            compose.mainClock.autoAdvance = true
        }
        verifyFrame("subtitle-quick-send") { compose.onNodeWithText(phrase).performClick() }
        compose.runOnIdle { assertEquals(phrase, model.state.value.editorText); assertFalse(model.state.value.editing) }
        verifyFrame("subtitle-update") { compose.runOnIdle { model.text("更新后的字幕") } }
        verifyFrame("subtitle-clear") { compose.onNodeWithTag("editor-clear").performClick() }
        compose.onNodeWithText(model.state.value.subtitlePlaceholder).assertIsDisplayed()
        compose.runOnIdle { assertEquals("", model.state.value.editorText) }
    }

    @Test fun programmaticInputChangesKeepFocusAndAcceptMoreTyping() {
        val model = show()
        compose.onNodeWithTag("speech-editor").performClick()
        compose.onNodeWithTag("speech-editor").assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.text("新的字幕") }
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        compose.onNodeWithTag("speech-editor").assertIsFocused().performTextInput("继续")
        compose.runOnIdle { assertEquals("新的字幕继续", model.state.value.editorText); assertEquals(6, model.state.value.editorValue.selection.start); assertTrue(model.state.value.editing) }
        capture("subtitle-input-update-mid")
        compose.mainClock.advanceTimeBy(900); compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("speech-editor").assertIsFocused()
    }
}
