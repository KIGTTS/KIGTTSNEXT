package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.plugin.api.*
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
class ExtensionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(count: Int = 0): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        app.getSharedPreferences("ui-debug", 0).edit().putInt(UiSlot.EDITOR_TOP_END.name, count).commit()
        return testAppModel(app).also { model -> compose.setContent { KigttsApp(model) } }
    }
    private fun right(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().right.value
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")

    @Test fun rightExtensionsGrowLeftwardAndOverflowRemainsReachable() {
        val model = show(2)
        assertTrue(right("editor-font-size") > right("debug.ui/EDITOR_TOP_END/0"))
        assertEquals(right("editor-area") - 8f, right("editor-font-size"), .5f)
        assertTrue(right("debug.ui/EDITOR_TOP_END/0") > right("debug.ui/EDITOR_TOP_END/1"))
        compose.runOnIdle { repeat(18) { model.invoke("debug.ui", "EDITOR_TOP_END/add") } }
        compose.onNodeWithTag("editor-font-size").assertIsDisplayed()
        compose.onNodeWithTag("debug.ui/EDITOR_TOP_END/19").assertDoesNotExist()
        compose.onNodeWithTag("editor-top-end-more").performClick()
        compose.onNodeWithTag("debug.ui/EDITOR_TOP_END/19-menu").performScrollTo().assertIsDisplayed()
        capture("right-extension-overflow")
        compose.onNodeWithTag("debug.ui/EDITOR_TOP_END/19-menu").performClick()
        compose.onNodeWithText("调试入口 · 字幕右上").assertIsDisplayed()
    }

    @Test fun bothFontControlsReplaceLeftButtonsWithoutDialog() {
        val model = show()
        compose.onNodeWithTag("editor-font-size").performClick()
        compose.onNodeWithTag("editor-flip").assertDoesNotExist()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithContentDescription("字幕字号滑条").performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        capture("subtitle-inline-font")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("editor-flip").assertIsDisplayed()
        compose.runOnIdle { assertEquals(12f, model.state.value.subtitleSizePercent); model.toggleListening() }
        compose.onNodeWithText("聆听文本").assertDoesNotExist()
        val caption = compose.onNodeWithTag("captions").getUnclippedBoundsInRoot()
        val font = compose.onNodeWithTag("listening-font-size").getUnclippedBoundsInRoot()
        assertTrue(font.top > caption.top + (caption.bottom - caption.top) / 2)
        assertTrue(right("listening-font-size") > right("panel.listening/close"))
        compose.onNodeWithTag("listening-font-size").performClick()
        compose.onNodeWithTag("panel.listening/mode").assertDoesNotExist()
        compose.onNodeWithContentDescription("聆听字号滑条").performSemanticsAction(SemanticsActions.SetProgress) { it(22f) }
        compose.onNode(isDialog()).assertDoesNotExist()
        capture("listening-inline-font")
        compose.onNodeWithContentDescription("完成聆听字号调整").performClick()
        compose.onNodeWithTag("panel.listening/mode").assertIsDisplayed()
        compose.runOnIdle { assertEquals(22f, model.state.value.captionTextSize) }
    }
    @Test fun tabEndExtensionsRemainFixedAndOverflowOpensMenu() {
        val model = show()
        compose.runOnIdle {
            repeat(8) { model.invoke("debug.ui", "TAB_END/add") }
            model.openPanel(HomePanel.QUICK_TEXT)
        }
        compose.onNodeWithTag("debug.ui/TAB_END/0").assertIsDisplayed()
        compose.onNodeWithTag("debug.ui/TAB_END/7").assertDoesNotExist()
        compose.onNodeWithTag("TAB_END-more").assertIsDisplayed()
        val rightEdge = right("debug.ui/TAB_END/0")
        compose.onNodeWithText("常用").performTouchInput { swipeLeft() }
        assertEquals(rightEdge, right("debug.ui/TAB_END/0"), .5f)
        compose.onNodeWithTag("TAB_END-more").performClick()
        compose.onNodeWithTag("debug.ui/TAB_END/7-menu").performScrollTo().assertIsDisplayed()
        capture("tab-extension-overflow")
    }

    @Test fun dockLongPressIsOwnedOnlyByEnabledVoicePlugin() {
        val model = show()
        compose.onNodeWithTag("dock-main").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        compose.onNodeWithTag("dock-main").performTouchInput { longClick() }
        compose.runOnIdle { assertNull(model.state.value.panel); assertFalse(model.state.value.captionsVisible) }
        compose.runOnIdle { model.invoke("input.voice", "toggle") }
        compose.onNodeWithTag("dock-main").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        compose.onNodeWithTag("dock-main").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithTag("voice-input-live").assertIsDisplayed()
        compose.runOnIdle { assertTrue(model.state.value.voiceInputActive); assertFalse(model.state.value.captionsVisible)
            model.endVoiceInput(false); model.invoke("input.voice", "toggle") }
        compose.onNodeWithTag("dock-main").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        compose.runOnIdle { assertTrue(model.registry.gestureActions(PluginGesture.QUICK_TEXT_LONG_PRESS, model.context()).isEmpty()) }
    }

    @Test fun veryLongSubtitleKeepsReadableSizeAndScrollsWithoutEnteringInput() {
        val model = show()
        val text = "很长的字幕需要保持可读，超过区域时可以滚动查看。".repeat(60)
        compose.runOnIdle { model.text(text); model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f) }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("speech-editor").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(24f, layouts.single().layoutInput.style.fontSize.value, .5f)
        assertEquals(text.length, layouts.single().layoutInput.text.length)
        compose.onNodeWithTag("speech-editor").performTouchInput { swipeUp() }
        compose.runOnIdle { assertFalse(model.state.value.editing); assertEquals(SubtitleSize.DEFAULT_PERCENT, model.state.value.subtitleSizePercent) }
        val range = compose.onNodeWithTag("speech-editor").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.value() > 0f)
        capture("subtitle-readable-scroll")
        compose.runOnIdle { model.setSubtitleSizePercent(1800f / 393f) }
        layouts.clear()
        compose.onNodeWithTag("speech-editor").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(18f, layouts.single().layoutInput.style.fontSize.value, .5f)
    }
}
