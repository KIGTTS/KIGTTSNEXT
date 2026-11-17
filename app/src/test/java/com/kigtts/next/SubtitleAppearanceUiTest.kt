package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.i18n.UiLanguage
import com.kigtts.next.state.*
import com.kigtts.next.text.*
import com.kigtts.next.ui.home.*
import com.kigtts.next.ui.components.ProvideIconTextContextMenu
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SubtitleAppearanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    @Before fun reset() { app.getSharedPreferences("subtitle-appearance", 0).edit().clear().commit() }
    private fun show(bodyOnly: Boolean = false, nativeWindow: Boolean = false): AppViewModel {
        val model = testAppModel(app)
        model.setAutoPlayback(false); model.text("测试字幕"); model.setSubtitleSizePercent(4f)
        val window = object : WindowInfo { override val isWindowFocused = true; override val containerSize = IntSize(400, 800) }
        compose.setContent {
            val state by model.state.collectAsState()
            if (nativeWindow) {
                MaterialTheme { ProvideIconTextContextMenu {
                    SubtitleBody(state, model, Modifier.fillMaxSize())
                } }
                return@setContent
            }
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalWindowInfo provides window, LocalUiLocale provides state.uiLocale) {
                MaterialTheme { ProvideIconTextContextMenu {
                    Box(Modifier.fillMaxSize()) {
                        if (bodyOnly) SubtitleBody(state, model, Modifier.width(350.dp).height(500.dp))
                        else TextEditor(state, model, false, false, Modifier.width(350.dp).height(500.dp))
                    }
                } }
            }
        }
        return model
    }
    @Test fun menuRemovesOldEntrancesAndOffersPersistentAppearanceAndNinePositions() {
        val model = show()
        assertEquals(SubtitleAppearance(), model.state.value.subtitleAppearance)
        compose.onNodeWithTag("editor-menu").performClick()
        listOf("流式识别", "完整录音识别", "文本编辑器", "页面文件", "选择图片、视频或网页").forEach {
            compose.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
        compose.onNodeWithText("打开粗体").performClick()
        compose.runOnIdle { assertTrue(model.state.value.subtitleAppearance.bold) }
        compose.onNodeWithTag("editor-menu").performClick()
        compose.onNodeWithTag("subtitle-alignment").performClick()
        SubtitleAlignment.entries.forEach { compose.onNodeWithTag("subtitle-align-" + it.name.lowercase()).assertIsDisplayed() }
        compose.onNodeWithTag("subtitle-align-bottom_right").performClick(); compose.waitForIdle()
        compose.onNodeWithTag("editor-menu").performClick()
        compose.onNodeWithText("开启富文本编辑").performClick()
        compose.runOnIdle {
            assertEquals(SubtitleAppearance(true, SubtitleAlignment.BOTTOM_RIGHT, true), SubtitleAppearanceStore(app).load())
            assertEquals("测试字幕", model.state.value.editorText)
        }
    }
    @Test fun menuAndAlignmentFollowAppLanguageWithoutChangingTheCaptions() {
        val model = show()
        compose.runOnIdle { model.setUiLanguage(UiLanguage.ENGLISH) }
        compose.onNodeWithTag("editor-menu").performClick()
        compose.onNodeWithText("Turn bold on").assertIsDisplayed()
        compose.onNodeWithText("Text alignment").assertIsDisplayed().performClick()
        compose.onNodeWithText("Top left").assertIsDisplayed()
        compose.onNodeWithText("Bottom right").assertIsDisplayed().performClick()

        compose.runOnIdle {
            model.setUiLanguage(UiLanguage.KOREAN)
            model.setSubtitleAppearance(model.state.value.subtitleAppearance.copy(richTextEditing = true))
        }
        compose.onNodeWithTag("editor-menu").performClick()
        compose.onNodeWithTag("subtitle-rich-editing").assert(hasText("서식 있는 텍스트 편집 끄기"))
        compose.runOnIdle {
            assertEquals(SubtitleAlignment.BOTTOM_RIGHT, model.state.value.subtitleAppearance.alignment)
            assertEquals("测试字幕", model.state.value.editorText)
        }
    }

    @Test fun allNinePositionsApplyToBothEditableAndDisplayTextWithoutChangingTheViewport() {
        val model = show(bodyOnly = true)
        listOf(false, true).forEach { editing ->
            compose.runOnIdle { model.editing(editing) }
            SubtitleAlignment.entries.forEach { alignment ->
                compose.runOnIdle { model.setSubtitleAppearance(SubtitleAppearance(alignment = alignment)) }
                val node = compose.onNodeWithTag("speech-editor")
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertEquals(FontWeight.Normal, layout.layoutInput.style.fontWeight)
                val bounds = if (editing) compose.onNodeWithTag("subtitle-input-glyphs", true).getUnclippedBoundsInRoot() else node.getUnclippedBoundsInRoot()
                when (alignment.row) {
                    0 -> assertEquals("$editing $alignment", 12f, bounds.top.value, 2f)
                    1 -> assertEquals("$editing $alignment", 250f, (bounds.top.value + bounds.bottom.value) / 2, 2f)
                    2 -> assertEquals("$editing $alignment", 488f, bounds.bottom.value, 2f)
                }
                when (alignment.column) {
                    0 -> assertEquals(0f, layout.getLineLeft(0), 2f)
                    1 -> assertEquals(layout.size.width / 2f, (layout.getLineLeft(0) + layout.getLineRight(0)) / 2, 2f)
                    2 -> assertEquals(layout.size.width.toFloat(), layout.getLineRight(0), 2f)
                }
            }
        }
    }
    @Test @Config(sdk = [27]) fun richTextSwitchControlsRealSelectionMenuAndPreservesFormattingWhenTurnedOff() {
        val model = show(bodyOnly = true, nativeWindow = true)
        compose.runOnIdle { model.editing(true) }
        fun open() {
            compose.onNodeWithTag("speech-editor").performTouchInput { longClick(center) }
            // Native word selection can cover this entire short Chinese phrase already.
            if (model.state.value.editorValue.selection.length < model.state.value.editorText.length)
                compose.onNodeWithTag("text-menu-select-all").performClick()
            compose.onNodeWithTag("text-menu-copy").assertIsDisplayed()
        }
        open(); compose.onNodeWithTag("text-menu-bold").assertDoesNotExist()
        compose.runOnIdle { model.setSubtitleAppearance(SubtitleAppearance(richTextEditing = true)) }
        compose.onNodeWithTag("text-menu-bold").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(model.state.value.subtitleFormats.any { it.kind == TextFormat.BOLD })
            model.setSubtitleAppearance(SubtitleAppearance(richTextEditing = false))
        }
        compose.onNodeWithTag("text-menu-bold").assertDoesNotExist()
        compose.runOnIdle {
            val before = model.state.value.subtitleFormats
            model.formatSubtitle(TextRange(0, 2), TextFormat.ITALIC)
            assertEquals(before, model.state.value.subtitleFormats)
        }
    }
}
