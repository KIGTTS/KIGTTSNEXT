package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import com.kigtts.next.theme.*
import com.kigtts.next.ui.sketch.*
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
class SketchUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): SketchState {
        val state = SketchState()
        compose.setContent { KigttsTheme(ThemeSettings()) { SketchScreen(state, 0, {}, {}) } }
        return state
    }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")

    @Test fun selectedToolsOpenSettingsOnSecondTapAndApplySizesAndTypes() {
        val state = show()
        compose.onNodeWithContentDescription("画笔").performClick()
        compose.onNodeWithText("画笔笔触").assertIsDisplayed()
        compose.onNodeWithContentDescription("画笔大小").performSemanticsAction(SemanticsActions.SetProgress) { it(14f) }
        compose.onNodeWithText("钢笔").performClick()
        compose.runOnIdle { assertEquals(14f, state.penSize); assertEquals(PenType.FOUNTAIN, state.penType) }
        capture("sketch-pen-menu")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("橡皮擦").performClick()
        compose.onNodeWithText("橡皮擦笔触").assertDoesNotExist()
        compose.onNodeWithContentDescription("橡皮擦").performClick()
        compose.onNodeWithContentDescription("橡皮擦大小").performSemanticsAction(SemanticsActions.SetProgress) { it(48f) }
        compose.onNodeWithText("整笔擦除").performClick()
        compose.runOnIdle { assertEquals(48f, state.eraserSize); assertEquals(EraserType.STROKE, state.eraserType) }
        capture("sketch-eraser-menu")
    }

    @Test fun currentColorRemainsFixedWhilePresetsScrollAndPickerAppliesHex() {
        val state = show()
        compose.onNodeWithContentDescription("画笔颜色").performClick()
        val current = compose.onNodeWithTag("current-sketch-color")
        val before = current.getUnclippedBoundsInRoot()
        compose.onNodeWithTag("sketch-preset-colors").performTouchInput { swipeLeft() }
        assertEquals(before, current.getUnclippedBoundsInRoot())
        compose.onNodeWithContentDescription("预设颜色 #F0A8D7").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0xFFF0A8D7, state.color) }
        capture("sketch-color-palette")
        current.performClick()
        compose.onNodeWithText("取色器").assertIsDisplayed()
        compose.onNodeWithContentDescription("HEX 颜色").performTextReplacement("00696E")
        compose.onNodeWithText("应用颜色").performClick()
        compose.runOnIdle { assertEquals(0xFF00696E, state.color) }
        compose.onNodeWithContentDescription("画笔颜色").performClick()
        compose.onNodeWithTag("current-sketch-color").performClick()
        compose.onNodeWithText("取色器").assertIsDisplayed()
        compose.onNode(isDialog()).captureRoboImage("build/previews/sketch-color-picker.png")
    }
    @Test fun colorPickerCanOpenAsNativeDialog() {
        compose.setContent { KigttsTheme(ThemeSettings()) { SketchColorPicker(0xFF65D7DE, {}, {}) } }
        compose.onNodeWithText("取色器").assertIsDisplayed()
    }
    @Test fun canvasTouchDrawsInkAndSelectedEraserRemovesPartOfIt() {
        val state = show()
        compose.runOnIdle { state.penSize = 12f; state.penType = PenType.FOUNTAIN }
        compose.onNodeWithTag("sketch-canvas").performTouchInput {
            swipe(start = center.copy(x = width * .15f), end = center.copy(x = width * .85f), durationMillis = 300)
        }
        compose.runOnIdle { assertTrue(state.strokes.any { !it.erase && it.points.size > 1 }) }
        compose.onNodeWithContentDescription("橡皮擦").performClick()
        compose.onNodeWithTag("sketch-canvas").performTouchInput {
            swipe(start = center.copy(y = height * .4f), end = center.copy(y = height * .6f), durationMillis = 300)
        }
        compose.runOnIdle { assertTrue(state.strokes.any { it.erase && it.points.size > 1 }); assertEquals(SketchTool.ERASER, state.tool) }
        capture("sketch-drawing-and-erasing")
    }
}
