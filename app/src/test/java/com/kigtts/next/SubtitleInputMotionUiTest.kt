package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.ui.home.SubtitleBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SubtitleInputMotionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(text: String = "第一行"): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "playback", "home-panel").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        val model = testAppModel(app)
        model.setAutoPlayback(false); model.setSubtitleSizePercent(12f); model.text(text); model.editing(true)
        val window = object : WindowInfo { override val isWindowFocused = true; override val containerSize = IntSize(400, 800) }
        compose.setContent {
            val state by model.state.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalWindowInfo provides window) {
                MaterialTheme { SubtitleBody(state, model, Modifier.width(350.dp).height(500.dp)) }
            }
        }
        compose.onNodeWithTag("speech-editor").performClick().performTextInputSelection(TextRange(text.length))
        compose.onNodeWithTag("speech-editor").assertIsFocused()
        return model
    }
    private fun capture(name: String) {
        java.io.File("build/previews").mkdirs()
        compose.onRoot().captureRoboImage("build/previews/$name.png")
    }
    @Test fun lineCountChangesKeepTheViewportStableWhileTextAndFocusUpdateImmediately() {
        val model = show()
        val before = compose.onNodeWithTag("speech-editor").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("speech-editor").performTextInput("\n第二行")
        compose.runOnIdle { assertEquals("第一行\n第二行", model.state.value.editorText) }
        compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        val middle = compose.onNodeWithTag("speech-editor").fetchSemanticsNode().boundsInRoot.height
        capture("subtitle-typing-lines-mid")
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        val end = compose.onNodeWithTag("speech-editor").fetchSemanticsNode().boundsInRoot.height
        assertEquals("The editor must not grow from the previous line's clipping bounds", before, middle, .1f)
        assertEquals(before, end, .1f)
        compose.onNodeWithTag("speech-editor").performTextInput("\n第三行\n第四行")
        compose.mainClock.advanceTimeBy(32)
        assertEquals(before, compose.onNodeWithTag("speech-editor").fetchSemanticsNode().boundsInRoot.height, .1f)
        capture("subtitle-typing-four-lines-mid")
        compose.onNodeWithTag("speech-editor").performTextReplacement("短字幕")
        compose.mainClock.advanceTimeBy(48)
        assertEquals(before, compose.onNodeWithTag("speech-editor").fetchSemanticsNode().boundsInRoot.height, .1f)
        capture("subtitle-deleting-lines-mid")
        compose.onNodeWithTag("speech-editor").assertIsFocused()
        compose.onAllNodesWithTag("speech-editor").assertCountEquals(1)
        compose.mainClock.autoAdvance = true
    }
    @Test fun displayTransitionsDoNotAnimateOrClipTheTextContainer() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app)
        model.setAutoPlayback(false); model.setSubtitleSizePercent(12f); model.text("旧字幕")
        val window = object : WindowInfo { override val isWindowFocused = true; override val containerSize = IntSize(400, 800) }
        compose.setContent {
            val state by model.state.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalWindowInfo provides window) {
                MaterialTheme { SubtitleBody(state, model, Modifier.width(350.dp).height(500.dp)) }
            }
        }
        val before = compose.onNodeWithTag("subtitle-text-transition", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.text("第一行\n第二行\n第三行\n第四行") }
        repeat(8) {
            compose.mainClock.advanceTimeBy(24); compose.waitForIdle()
            assertEquals(before, compose.onNodeWithTag("subtitle-text-transition", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        }
        capture("subtitle-display-four-lines")
        compose.runOnIdle { model.text("") }
        compose.mainClock.advanceTimeBy(48)
        assertEquals(before, compose.onNodeWithTag("subtitle-text-transition", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        capture("subtitle-display-clear-mid")
        compose.mainClock.autoAdvance = true
    }
    @Test fun clearingKeepsOneNativeEditorAndNewTypingCancelsTheDepartingFrame() {
        val model = show("旧字幕\n第二行")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { model.text("") }
        compose.mainClock.advanceTimeBy(48); compose.waitForIdle()
        assertEquals("", compose.onNodeWithTag("speech-editor").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onAllNodesWithTag("speech-editor").assertCountEquals(1)
        capture("subtitle-input-clear-mid")
        compose.onNodeWithTag("speech-editor").assertIsFocused().performTextInput("新的文字")
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        compose.runOnIdle { assertEquals("新的文字", model.state.value.editorText); assertEquals(TextRange(4), model.state.value.editorValue.selection) }
        compose.mainClock.advanceTimeBy(400); compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("subtitle-placeholder", useUnmergedTree = true).assertDoesNotExist()
        capture("subtitle-input-clear-interrupted")
    }
    @Test fun composingValuesAndCommitSurviveAnimationFramesAndSelectionChanges() {
        val model = show("")
        compose.runOnIdle {
            model.editText(TextFieldValue("ni", TextRange(2), TextRange(0, 2)))
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("ni", model.state.value.editorText); assertNotNull(model.state.value.editorValue.composition) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            model.editText(TextFieldValue("你", TextRange(1)))
        }
        compose.mainClock.advanceTimeBy(48); compose.waitForIdle()
        compose.runOnIdle { assertEquals("你", model.state.value.editorText); assertNull(model.state.value.editorValue.composition) }
        compose.onNodeWithTag("speech-editor").performTextInputSelection(TextRange(0, 1))
        compose.mainClock.advanceTimeBy(200); compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(TextRange(0, 1), model.state.value.editorValue.selection) }
        compose.onNodeWithTag("speech-editor").assertIsFocused()
    }
    @Test fun departingLongPlaceholderKeepsItsOwnFontSizeWhenTheFirstGlyphIsTyped() {
        val model = show("")
        compose.runOnIdle { model.setSubtitlePlaceholder("这是一段需要缩小才能显示的提示文字".repeat(12)) }
        compose.waitForIdle()
        fun hintSize(): Float {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("subtitle-placeholder", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().layoutInput.style.fontSize.value
        }
        val before = hintSize()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("speech-editor").performTextInput("你")
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        assertEquals("The fading hint must not jump to the new glyph's larger font", before, hintSize(), .01f)
        compose.onNodeWithTag("speech-editor").assertIsFocused()
        compose.mainClock.advanceTimeBy(160); compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("subtitle-placeholder", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test fun presentingTheSameTextAgainDoesNotStartAnotherFade() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = testAppModel(app)
        model.setAutoPlayback(false); model.text("保持清晰的字幕")
        compose.setContent {
            val state by model.state.collectAsState()
            MaterialTheme { SubtitleBody(state, model, Modifier.width(350.dp).height(500.dp)) }
        }
        compose.mainClock.autoAdvance = false
        repeat(6) {
            compose.runOnIdle { model.text("保持清晰的字幕") }
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            compose.onAllNodesWithTag("speech-editor", useUnmergedTree = true).assertCountEquals(1)
        }
        compose.mainClock.autoAdvance = true
    }
    @Test fun viewportReboundsDoNotAlternateTheFittedGlyphSize() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        val text = "第一行\n第二行\n第三行\n第四行"
        var height by mutableIntStateOf(500)
        val initialState = model.state.value
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(Modifier.width(350.dp).height(500.dp)) {
                        com.kigtts.next.ui.home.SubtitleDisplay(initialState, model, text,
                            androidx.compose.ui.text.TextStyle(fontSize = 72.sp, lineHeight = 1.25.em), 350, height)
                    }
                }
            }
        }
        fun glyphSize(): Float {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("speech-editor").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().layoutInput.style.fontSize.value
        }
        val original = glyphSize()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { height = 170 }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(16, java.util.concurrent.TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(48); compose.waitForIdle()
        capture("subtitle-rebound-shrink")
        val fitted = glyphSize()
        assertTrue("Smaller viewport must fit the glyphs: $original -> $fitted", fitted < original)
        for (rebound in listOf(180, 174, 184, 177)) {
            compose.runOnIdle { height = rebound }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(16, java.util.concurrent.TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
            assertEquals("Spring rebounds must not enlarge the glyphs mid-motion", fitted, glyphSize(), .01f)
        }
        compose.runOnIdle { height = 500 }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(240, java.util.concurrent.TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(240); compose.waitForIdle()
        assertEquals(original, glyphSize(), .01f)
        compose.mainClock.autoAdvance = true
    }
}
