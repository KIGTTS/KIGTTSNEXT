package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
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
        val state = SketchState().apply { initializeCanvas(IntSize(1179, 2556)) }
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

    @Test fun currentColorSwatchOpensTheSharedHsvPickerDirectlyAndAppliesPresetsAndHex() {
        val state = show()
        compose.onNodeWithContentDescription("画笔颜色").performClick()
        compose.onNodeWithText("取色器").assertIsDisplayed()
        compose.onNodeWithTag("current-sketch-color").assertDoesNotExist()
        compose.onNodeWithContentDescription("HEX 颜色").assertTextContains("65D7DE")
        compose.onNodeWithContentDescription("预设颜色 #FF6B6B").performClick()
        compose.onNodeWithContentDescription("HEX 颜色").assertTextContains("FF6B6B")
        compose.onNodeWithContentDescription("HEX 颜色").performTextReplacement("00696E")
        compose.onNodeWithText("应用颜色").performClick()
        compose.runOnIdle { assertEquals(0xFF00696E, state.color) }
        compose.onNodeWithContentDescription("画笔颜色").performClick()
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

    @Test fun twoFingerCanvasNavigationAndToolbarClicksNeverLeaveInk() {
        val state = show()
        compose.onNodeWithTag("sketch-canvas").performTouchInput {
            down(0, center - Offset(50f, 0f)); down(1, center + Offset(50f, 0f))
            moveTo(0, center - Offset(100f, -30f)); moveTo(1, center + Offset(100f, 30f))
            advanceEventTime(32); up(1); up(0)
        }
        compose.runOnIdle { assertTrue(state.viewportZoom > 1.5f); assertTrue(state.strokes.isEmpty()) }
        compose.onNodeWithContentDescription("橡皮擦").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(SketchTool.ERASER, state.tool); assertTrue(state.strokes.isEmpty()) }
        compose.onNodeWithTag("sketch-toolbar-horizontal").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "zh-rCN-w852dp-h393dp-xxhdpi")
    fun landscapePlacesNativeToolbarOnTheRightAndKeepsThePortraitDocumentGeometry() {
        val state = show()
        val toolbar = compose.onNodeWithTag("sketch-toolbar-vertical").fetchSemanticsNode().boundsInRoot
        val canvas = compose.onNodeWithTag("sketch-canvas").fetchSemanticsNode().boundsInRoot
        assertTrue(toolbar.height > toolbar.width)
        assertEquals(canvas.center.y, toolbar.center.y, 3f)
        assertTrue(toolbar.left > canvas.center.x)
        compose.onNodeWithTag("sketch-toolbar-horizontal").assertDoesNotExist()
        compose.runOnIdle { assertEquals(IntSize(1179, 2556), state.documentSize) }
        capture("sketch-landscape-original-document")
    }
}
