package com.kigtts.next

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.theme.*
import com.kigtts.plugin.api.UiSlot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PrototypeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("ui-debug", 0).edit().clear().commit()
        app.getSharedPreferences("appearance", 0).edit().clear().commit()
        app.getSharedPreferences("home-panel", 0).edit().clear().commit()
        app.getSharedPreferences("voice-input", 0).edit().clear().commit()
        val model = testAppModel(app)
        compose.setContent { KigttsApp(model) }
        return model
    }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")

    @Test fun defaultHomeContainsOnlyReferenceControlsAndLogo() {
        val model = show()
        compose.onNodeWithContentDescription("KIGTTS 横版 Logo").assertIsDisplayed()
        compose.onNodeWithContentDescription("聆听插件").assertDoesNotExist()
        compose.onNodeWithTag("captions").assertDoesNotExist()
        compose.runOnIdle { assertTrue(UiSlot.entries.all { slot -> model.registry.actions(slot).none { it.plugin.descriptor.id == "debug.ui" } }) }
        capture("home-collapsed")
        compose.onNodeWithContentDescription("文本菜单").performClick()
        compose.onNodeWithText("完整录音识别（最多 8 秒）").assertExists()
        capture("editor-menu")
    }

    @Test fun drawerOpensPluginOwnedPageAndDebugSettings() {
        val model = show()
        compose.onNodeWithContentDescription("打开抽屉菜单").performClick()
        compose.onNodeWithText("聆听插件").performScrollTo().performClick()
        compose.onNodeWithText("把声音变成文字").assertExists()
        capture("plugin-page")
        compose.runOnIdle { model.navigate(Destination.PluginPage("debug.ui", "config")) }
        compose.onNodeWithText("增加 · 顶栏左侧").performScrollTo().performClick()
        compose.onNodeWithText("当前数量：1").assertExists()
        compose.runOnIdle {
            assertEquals(1, model.registry.actions(UiSlot.HEADER_START).size)
            model.navigate(Destination.Home)
        }
        compose.onNodeWithContentDescription("顶栏左侧 · 1").assertIsDisplayed().performClick()
        compose.onNodeWithText("调试入口 · 顶栏左侧").assertExists()
        compose.onNodeWithText("显示对话框").performClick()
        compose.onNodeWithText("调试按钮").assertExists()
        capture("debug-dialog")
    }

    @Test fun bottomButtonOpensOneRowThenDraggingExpandsGridAndCollapses() {
        val model = show()
        compose.onNodeWithTag("dock-main").performClick()
        compose.onNodeWithTag("phrases-horizontal").assertIsDisplayed()
        capture("quick-one-row")
        val before = model.state.value.panelHeight
        compose.onNodeWithTag("sheet-handle").performTouchInput { swipeUp(startY = centerY, endY = centerY - 210f, durationMillis = 500) }
        compose.runOnIdle { assertTrue(model.state.value.panelHeight > before) }
        compose.onNodeWithTag("phrases-vertical").assertIsDisplayed()
        compose.onNodeWithText("常用").assertIsDisplayed()
        capture("quick-expanded")
        compose.onNodeWithTag("sheet-handle").performSemanticsAction(SemanticsActions.SetProgress) { it(184f) }
        compose.onNodeWithTag("phrases-horizontal").assertIsDisplayed()
        compose.onNodeWithContentDescription("收起快捷文本").performClick()
        compose.onNodeWithTag("phrases-horizontal").assertDoesNotExist()
        compose.runOnIdle { assertNull(model.state.value.panel); assertEquals(Destination.Home, model.state.value.destination) }
    }

    @Test fun canvasAndCardsExpandOverTheSameHomePage() {
        val model = show()
        compose.onNodeWithContentDescription("画板").performClick()
        compose.onNodeWithContentDescription("画笔").assertIsDisplayed()
        compose.runOnIdle { assertEquals(Destination.Home, model.state.value.destination) }
        capture("sketch-page")
        compose.onNodeWithContentDescription("返回大字幕").performClick()
        compose.onNodeWithContentDescription("名片").performClick()
        compose.onNodeWithContentDescription("添加名片").assertIsDisplayed()
        capture("cards-page")
    }

    @Test fun inputFillsAvailableScreenAndRestoresPreviousSheet() {
        val model = show()
        compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f) }
        compose.runOnIdle { model.toggleListening(); model.captionHeight(190f) }
        compose.onNodeWithTag("captions").assertIsDisplayed()
        compose.onNodeWithTag("phrases-vertical").assertIsDisplayed()
        compose.onNodeWithTag("speech-editor").performClick().performTextInput("输入测试")
        compose.onNodeWithContentDescription("KIGTTS 横版 Logo").assertDoesNotExist()
        compose.onNodeWithTag("captions").assertDoesNotExist()
        compose.onNodeWithTag("home-sheet").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(model.state.value.captionsVisible)
            assertEquals(190f, model.state.value.captionHeight)
            assertEquals(HomePanel.QUICK_TEXT, model.state.value.panel)
            assertEquals(340f, model.state.value.panelHeight)
        }
        val editor = compose.onNodeWithTag("editor-area").getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertEquals((root.bottom - root.top).value, (editor.bottom - editor.top).value, .5f)
        capture("editing-full-screen")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("KIGTTS 横版 Logo").assertIsDisplayed()
        compose.onNodeWithTag("phrases-vertical").assertIsDisplayed()
        compose.onNodeWithTag("captions").assertIsDisplayed()
        compose.runOnIdle { assertFalse(model.state.value.editing); assertEquals(340f, model.state.value.panelHeight) }
    }

    @Test fun expressivePressExpandsButtonCompressesNeighborAndSpringsBack() {
        show()
        val button = compose.onNodeWithTag("editor-flip")
        val neighbor = compose.onNodeWithTag("editor-menu")
        val width = button.getUnclippedBoundsInRoot().let { (it.right - it.left).value }
        val neighborWidth = neighbor.getUnclippedBoundsInRoot().let { (it.right - it.left).value }
        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(180)
        compose.waitForIdle()
        assertTrue(button.getUnclippedBoundsInRoot().let { (it.right - it.left).value } > width + 1f)
        assertTrue(neighbor.getUnclippedBoundsInRoot().let { (it.right - it.left).value } < neighborWidth - .5f)
        capture("elastic-button-held")
        button.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        assertEquals(width, button.getUnclippedBoundsInRoot().let { (it.right - it.left).value }, .5f)
        capture("elastic-button-released")
        compose.mainClock.autoAdvance = true
    }

    @Test fun listeningPluginRevealsResizablePanelAndBothSubtitlesPinch() {
        val model = show()
        compose.onNodeWithTag("captions").assertDoesNotExist()
        val original = model.state.value.subtitleSizePercent
        compose.onNodeWithTag("subtitle-content").performTouchInput {
            down(0, center - Offset(30f, 0f)); down(1, center + Offset(30f, 0f))
            moveBy(0, Offset(-15f, 0f)); moveBy(1, Offset(15f, 0f)); up(0); up(1)
        }
        compose.runOnIdle { assertTrue(model.state.value.subtitleSizePercent > original); assertFalse(model.state.value.editing) }
        compose.onNodeWithTag("panel.listening/toggle").performClick()
        compose.onNodeWithTag("captions").assertIsDisplayed()
        compose.onNodeWithTag("top-panel-resize").performSemanticsAction(SemanticsActions.SetProgress) { it(190f) }
        val captionSize = model.state.value.captionTextSize
        compose.onNodeWithTag("listening-content").performTouchInput {
            down(0, center - Offset(25f, 0f)); down(1, center + Offset(25f, 0f))
            moveBy(0, Offset(-12f, 0f)); moveBy(1, Offset(12f, 0f)); up(0); up(1)
        }
        compose.runOnIdle { assertEquals(190f, model.state.value.captionHeight); assertTrue(model.state.value.captionTextSize > captionSize) }
        capture("listening-panel")
        compose.onNodeWithContentDescription("收起聆听面板").performClick()
        compose.onNodeWithTag("captions").assertDoesNotExist()
    }

    @Test fun flipNormalizesWhileEditingAndFontSlidersWork() {
        val model = show()
        compose.onNodeWithContentDescription("上下翻转字幕").performClick()
        compose.onNodeWithTag("subtitle-content").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "倒置字幕"))
        capture("flipped-subtitle")
        compose.onNodeWithContentDescription("调整字幕字号").performClick()
        compose.onNodeWithContentDescription("字幕字号滑条").performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        compose.onNodeWithContentDescription("完成字幕字号调整").performClick()
        compose.onNodeWithTag("speech-editor").performClick()
        compose.onNodeWithTag("subtitle-content").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正向字幕"))
        compose.onNodeWithContentDescription("移动光标").assertIsDisplayed()
        compose.runOnIdle { model.text("ABCDEF") }
        compose.onNodeWithTag("cursor-track").performTouchInput { swipeLeft(startX = centerX, endX = centerX - 90f, durationMillis = 500) }
        compose.runOnIdle { assertTrue(model.state.value.editorValue.selection.start < 6); assertEquals(12f, model.state.value.subtitleSizePercent) }
        capture("editing-cursor-track")
    }

    @Test fun predictiveReturnCancellationKeepsPageAndCommitReturnsHome() {
        val model = show()
        compose.runOnIdle { model.openSidePage(SidePage.SKETCH) }
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, .5f, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
        capture("predictive-return")
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.runOnIdle { assertEquals(SidePage.SKETCH, model.state.value.sidePage) }
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.runOnIdle { assertNull(model.state.value.sidePage) }
        listOf(Destination.Settings, Destination.Plugins, Destination.PluginPage("debug.ui", "config")).forEach { destination ->
            compose.runOnIdle { model.navigate(destination) }
            compose.runOnIdle { dispatcher.onBackPressed() }
            compose.runOnIdle { assertEquals(Destination.Home, model.state.value.destination) }
        }
    }

    @Test fun appearanceUsesThreeContrastAndCornerLevelsAndPersists() {
        val model = show()
        compose.runOnIdle { model.navigate(Destination.Settings) }
        capture("settings")
        compose.onNodeWithText("外观").performClick()
        compose.onNodeWithContentDescription("对比度：中").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ContrastLevel.MEDIUM, model.state.value.theme.contrast) }
        compose.onNodeWithContentDescription("对比度：高").performClick()
        compose.onNodeWithContentDescription("更暗的深色模式").performScrollTo().performClick()
        compose.onNodeWithContentDescription("圆角：大").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("圆角：小").performClick().assertIsSelected()
        compose.onNodeWithText("小").assertIsDisplayed()
        compose.onNodeWithText("小 4").assertDoesNotExist()
        compose.onNodeWithText("大 24").assertDoesNotExist()
        compose.runOnIdle { assertEquals(4f, model.state.value.theme.cornerRadius) }
        compose.onNodeWithContentDescription("圆角：中").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(14f, model.state.value.theme.cornerRadius) }
        compose.onNodeWithContentDescription("圆角：大").performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(ContrastLevel.HIGH, model.state.value.theme.contrast)
            assertTrue(model.state.value.theme.deeperDark)
            assertEquals(CornerLevel.LARGE, model.state.value.theme.cornerLevel)
            assertEquals(24f, model.state.value.theme.cornerRadius)
            assertEquals(model.state.value.theme, ThemeSettingsStore(ApplicationProvider.getApplicationContext()).load())
        }
        capture("settings-adjusted")
    }

    @Test fun editorRemainsUsableInKeyboardSizedViewport() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { Box(Modifier.size(320.dp, 360.dp)) { KigttsApp(model) } }
        compose.onNodeWithTag("speech-editor").performClick()
        compose.onNodeWithTag("home-sheet").assertDoesNotExist()
        compose.onNodeWithText("清空").assertIsDisplayed().performClick()
        compose.onNodeWithText("输入想说的话").assertIsDisplayed()
        capture("keyboard-viewport")
    }
}
