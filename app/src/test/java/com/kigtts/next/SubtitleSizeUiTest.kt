package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
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
class SubtitleSizeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun model(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "interaction", "quick-phrases").forEach {
            app.getSharedPreferences(it, 0).edit().clear().commit()
        }
        return testAppModel(app)
    }
    @Test fun percentagePersistsAndActualGlyphWidthTracksWindowWidthAcrossFontScales() {
        val model = model()
        var pixels by mutableStateOf(IntSize(1179, 2556))
        var fontScale by mutableFloatStateOf(1f)
        val info = object : WindowInfo {
            override val isWindowFocused = true
            override val containerSize: IntSize get() = pixels
        }
        compose.setContent {
            CompositionLocalProvider(LocalWindowInfo provides info, LocalDensity provides Density(3f, fontScale)) { KigttsApp(model) }
        }
        compose.runOnIdle { model.text("字") }
        compose.onNodeWithTag("editor-font-size").performClick()
        compose.onNodeWithContentDescription("字幕字号滑条").performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        compose.onNodeWithContentDescription("完成字幕字号调整").performClick()
        fun width(): Float {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("speech-editor").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().getBoundingBox(0).width
        }
        val before = width()
        assertEquals(1179f * .12f, before, 1.5f)
        compose.runOnIdle { pixels = IntSize(2358, 2556) }
        assertEquals(before * 2f, width(), 2f)
        compose.runOnIdle { fontScale = 2f }
        assertEquals(2358f * .12f, width(), 2f)
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(12f, testAppModel(app).state.value.subtitleSizePercent)
        assertEquals(12f, app.getSharedPreferences("appearance", 0).getFloat("subtitleWidthPercent", -1f))
        compose.onRoot().captureRoboImage("build/previews/subtitle-window-percentage.png")
        compose.runOnIdle { model.text("长文本".repeat(200)) }
        width()
        assertEquals(12f, testAppModel(app).state.value.subtitleSizePercent)
    }
    @Test fun listeningFontBarAndLargeContentKeepTheirReservedLayout() {
        val model = model()
        compose.setContent { KigttsApp(model) }
        compose.runOnIdle { model.toggleListening(); model.captionHeight(112f) }
        val initial = compose.onNodeWithTag("listening-card").getUnclippedBoundsInRoot()
        val controls = compose.onNodeWithTag("listening-controls").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("listening-font-size").performClick()
        val thumb = compose.onNodeWithTag("font-thumb-聆听字号", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(24f, (thumb.bottom - thumb.top).value, .5f)
        assertTrue(thumb.top >= controls.top && thumb.bottom <= controls.bottom)
        compose.onNodeWithContentDescription("聆听字号滑条").performSemanticsAction(SemanticsActions.SetProgress) { it(64f) }
        assertEquals(initial, compose.onNodeWithTag("listening-card").getUnclippedBoundsInRoot())
        assertEquals(controls, compose.onNodeWithTag("listening-controls").getUnclippedBoundsInRoot())
        compose.onRoot().captureRoboImage("build/previews/listening-large-font-inline-uncompressed.png")
        compose.onNodeWithContentDescription("完成聆听字号调整").performClick()
        compose.onNodeWithTag("panel.listening/mode").assertIsDisplayed()
        compose.onNodeWithContentDescription("打开设置").performClick()
        compose.onNodeWithText("外观与交互").assertIsDisplayed()
        compose.onNodeWithTag("app-drawer").assertDoesNotExist()
        compose.onNodeWithText("KIGTTS NEXT").assertDoesNotExist()
        compose.onNodeWithText("自由表达，由你掌握").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/previews/settings-direct-header.png")
    }
}
