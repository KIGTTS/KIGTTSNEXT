package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.home.SubtitleBody
import com.kigtts.next.ui.home.SubtitleDisplay
import com.kigtts.next.ui.home.SUBTITLE_READABLE_MINIMUM
import com.kigtts.next.ui.settings.AppearanceSettingsScreen
import com.github.takahirom.roborazzi.captureRoboImage
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
class SubtitlePlaceholderUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun model(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "media-presentation").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app)
    }
    @Test fun startupHintIsDisplayOnlyAndTypingRemovesItWithoutInsertingText() {
        val model = model()
        compose.setContent { KigttsApp(model) }
        compose.onRoot().captureRoboImage("build/previews/startup-placeholder.png")
        compose.onNodeWithTag("subtitle-placeholder", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals("", model.state.value.editorText); assertEquals("", model.state.value.transcript) }
        compose.onNodeWithTag("speech-editor").performClick()
        compose.onNodeWithTag("speech-editor").assertIsFocused()
        val editable = compose.onNodeWithTag("speech-editor").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("", editable)
        compose.onNodeWithTag("speech-editor").performTextInput("你好")
        compose.onNodeWithTag("subtitle-placeholder", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals("你好", model.state.value.editorText) }
    }
    @Test fun savingAndCancellingSettingsDialogHaveSeparateEffectsIncludingEmptyHint() {
        var value by mutableStateOf("原提示")
        compose.setContent { MaterialTheme { AppearanceSettingsScreen(ThemeSettings(), {}, value) { value = it } } }
        fun open() = compose.onNodeWithTag("subtitle-placeholder-setting").performScrollTo().performClick()
        open(); compose.onNodeWithTag("subtitle-placeholder-input").performTextReplacement("未保存")
        compose.onNodeWithTag("subtitle-placeholder-cancel").performClick()
        compose.runOnIdle { assertEquals("原提示", value) }
        open(); compose.onNodeWithTag("subtitle-placeholder-input").performTextReplacement("新提示\n第二行")
        compose.onNodeWithTag("subtitle-placeholder-save").performClick()
        compose.runOnIdle { assertEquals("新提示\n第二行", value) }
        open(); compose.onNodeWithTag("subtitle-placeholder-input").performTextClearance()
        compose.onNodeWithTag("subtitle-placeholder-save").performClick()
        compose.runOnIdle { assertEquals("", value) }
        compose.onNodeWithText("不显示").assertExists()
    }
    private fun layout(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    @Test fun placeholderFitsPanelHeightAndRestoresPreferredSizeWithoutChangingDocument() {
        val model = model()
        var height by mutableIntStateOf(240)
        val percent = model.state.value.subtitleSizePercent
        val state = AppState(subtitlePlaceholder = "点击输入\n第二行")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme { Box(Modifier.width(350.dp).height(height.dp)) {
                    SubtitleDisplay(state, model, "", TextStyle(fontSize = 80.sp, lineHeight = 1.25.em, textAlign = TextAlign.Center), 350, height)
                } }
            }
        }
        assertEquals(80f, layout("speech-editor").layoutInput.style.fontSize.value, .01f)
        compose.runOnIdle { height = 120 }
        val reduced = layout("speech-editor")
        assertTrue(reduced.layoutInput.style.fontSize.value in SUBTITLE_READABLE_MINIMUM..<80f)
        assertTrue(reduced.size.height <= 120)
        compose.runOnIdle { height = 20 }
        val bounded = layout("speech-editor")
        assertEquals(SUBTITLE_READABLE_MINIMUM, bounded.layoutInput.style.fontSize.value, .01f)
        assertTrue(bounded.size.height > 20)
        compose.runOnIdle { height = 240 }
        compose.mainClock.advanceTimeBy(240); compose.waitForIdle()
        assertEquals(80f, layout("speech-editor").layoutInput.style.fontSize.value, .01f)
        assertEquals("", model.state.value.editorText)
        assertEquals(percent, model.state.value.subtitleSizePercent, .01f)
    }

    @Test fun inputPlaceholderAlsoFitsAvailableHeightWhileEditableValueStaysEmpty() {
        val model = model()
        model.setSubtitlePlaceholder("输入\n文字")
        model.setSubtitleSizePercent(20f)
        model.editing(true)
        // Leave ample height for bold CJK fallback metrics and the body's vertical padding.
        var height by mutableIntStateOf(320)
        val window = object : WindowInfo {
            override val isWindowFocused = true
            override val containerSize = IntSize(400, 800)
        }
        compose.setContent {
            val state by model.state.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalWindowInfo provides window) {
                MaterialTheme { SubtitleBody(state, model, Modifier.width(350.dp).height(height.dp)) }
            }
        }
        assertEquals(80f, layout("subtitle-placeholder").layoutInput.style.fontSize.value, .01f)
        compose.runOnIdle { height = 100 }
        val reduced = layout("subtitle-placeholder")
        assertTrue(reduced.layoutInput.style.fontSize.value in SUBTITLE_READABLE_MINIMUM..<80f)
        // SubtitleBody reserves 12dp on each vertical side before fitting.
        assertTrue(reduced.size.height <= 76)
        assertEquals(reduced.layoutInput.style.fontSize.value, layout("speech-editor").layoutInput.style.fontSize.value, .01f)
        compose.runOnIdle { height = 320 }
        compose.mainClock.advanceTimeBy(240); compose.waitForIdle()
        assertEquals(80f, layout("subtitle-placeholder").layoutInput.style.fontSize.value, .01f)
        assertEquals("", compose.onNodeWithTag("speech-editor").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals("", model.state.value.editorText)
        assertEquals("", model.state.value.transcript)
        assertEquals(20f, model.state.value.subtitleSizePercent, .01f)
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(20f, app.getSharedPreferences("appearance", 0).getFloat("subtitleWidthPercent", -1f), .01f)
    }

    @Test fun shortPlaceholderTracksPreferredWindowPercentageInDisplayAndInputModes() {
        val model = model()
        model.setSubtitlePlaceholder("字")
        model.setSubtitleSizePercent(12f)
        var fontScale by mutableFloatStateOf(1f)
        val window = object : WindowInfo {
            override val isWindowFocused = true
            override val containerSize = IntSize(400, 800)
        }
        compose.setContent {
            val state by model.state.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale), LocalWindowInfo provides window) {
                MaterialTheme { SubtitleBody(state, model, Modifier.width(350.dp).height(240.dp)) }
            }
        }
        fun glyphWidth(tag: String) = layout(tag).getBoundingBox(0).width
        assertEquals(400f * .12f, glyphWidth("speech-editor"), 1f)
        compose.runOnIdle { model.setSubtitleSizePercent(20f) }
        assertEquals(400f * .2f, glyphWidth("speech-editor"), 1f)
        compose.runOnIdle { fontScale = 2f }
        assertEquals(400f * .2f, glyphWidth("speech-editor"), 1f)
        compose.runOnIdle { model.editing(true) }
        assertEquals(400f * .2f, glyphWidth("subtitle-placeholder"), 1f)
        assertEquals("", model.state.value.editorText)
        assertEquals(20f, model.state.value.subtitleSizePercent, .01f)
    }
}
