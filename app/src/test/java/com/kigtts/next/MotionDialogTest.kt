package com.kigtts.next

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.ui.components.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class MotionDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun step(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    @Test fun modalPagesSlideInOppositeDirectionsAndSystemBackKeepsTheSameWindow() {
        var child by mutableStateOf(false)
        var dismissed = false
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            MotionAlertDialog({ dismissed = true }, onNavigateUp = if (child) ({ child = false }) else null,
                text = { ModalPageTransition(child, { if (it) 1 else 0 }, Modifier.fillMaxWidth()) { visible ->
                    Box(Modifier.fillMaxWidth().height(128.dp).padding(horizontal = 48.dp)) {
                        Text(if (visible) "子页" else "首页", Modifier.testTag(if (visible) "child" else "parent"))
                    }
                } }, confirmButton = { TextButton({ child = true }) { Text("进入") } })
        } }
        step(16); step(16); step(500)
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        val parent = compose.onNodeWithTag("parent")
        val childNode = compose.onNodeWithTag("child")
        val left = parent.fetchSemanticsNode().boundsInRoot.left
        compose.runOnIdle { child = true }
        repeat(4) {
            if (!childNode.isDisplayed()) step(16)
        }
        val enteringChild = childNode.fetchSemanticsNode().boundsInRoot.left
        assertTrue("Child should enter from the right: $enteringChild vs $left", enteringChild > left)
        step(500)
        parent.assertDoesNotExist()
        assertEquals(left, childNode.fetchSemanticsNode().boundsInRoot.left, .5f)
        compose.runOnIdle { dialog.onBackPressedDispatcher.onBackPressed() }
        repeat(4) {
            if (!parent.isDisplayed()) step(16)
        }
        val enteringParent = parent.fetchSemanticsNode().boundsInRoot.left
        assertTrue("Parent should enter from the left: $enteringParent vs $left", enteringParent < left)
        step(500)
        childNode.assertDoesNotExist()
        assertFalse(dismissed)
        assertSame(dialog, ShadowDialog.getLatestDialog())
    }
    @Test fun contentAndBoundsAnimateAndConfirmWaitsUntilExitFinishesOnce() {
        val shown = mutableStateOf(true); var confirmed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { if (shown.value) MotionAlertDialog({ shown.value = false },
            title = { Text("确认操作") }, text = { Column {
                Text("弹窗内容")
                Box(Modifier.width(120.dp).height(44.dp).dialogElementMorph()
                    .background(MaterialTheme.colorScheme.primary).testTag("dialog-large-visual"))
            } }, confirmButton = {
                TextButton(dialogFeedbackClick { confirmed++; shown.value = false }) { Text("确定") }
            }) } }
        step(16); step(16); step(64)
        val container = compose.onNodeWithTag("alert-dialog-container")
        val middle = container.fetchSemanticsNode().boundsInRoot
        val middleText = compose.onNodeWithText("弹窗内容").fetchSemanticsNode().boundsInRoot
        val middleButton = compose.onNodeWithText("确定").fetchSemanticsNode().boundsInRoot
        val middleVisual = compose.onNodeWithTag("dialog-large-visual").fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).captureRoboImage("build/previews/dialog-enter-mid.png")
        step(400)
        val full = container.fetchSemanticsNode().boundsInRoot
        val fullText = compose.onNodeWithText("弹窗内容").fetchSemanticsNode().boundsInRoot
        val fullButton = compose.onNodeWithText("确定").fetchSemanticsNode().boundsInRoot
        val fullVisual = compose.onNodeWithTag("dialog-large-visual").fetchSemanticsNode().boundsInRoot
        assertEquals("Content glyphs keep their width during expansion", fullText.width, middleText.width, .5f)
        assertEquals("Content glyphs keep their height during expansion", fullText.height, middleText.height, .5f)
        assertEquals("Action glyphs keep their height during expansion", fullButton.height, middleButton.height, .5f)
        assertTrue("Large visuals may morph independently of glyphs", middleVisual.height < fullVisual.height)
        assertTrue("Vertical expansion: $middle -> $full", middle.bottom - middle.top < full.bottom - full.top)
        compose.onNode(isDialog()).captureRoboImage("build/previews/dialog-expanded.png")
        compose.onNodeWithText("确定").performClick(); compose.onNodeWithText("确定").performClick()
        step(64)
        assertEquals(0, confirmed); assertTrue(shown.value)
        assertTrue(container.fetchSemanticsNode().boundsInRoot.height < full.height)
        val exitingText = compose.onNodeWithText("弹窗内容").fetchSemanticsNode().boundsInRoot
        assertEquals("Content keeps its height during dismissal", fullText.height, exitingText.height, .5f)
        compose.onNode(isDialog()).captureRoboImage("build/previews/dialog-exit-mid.png")
        step(400); assertEquals(1, confirmed); compose.onNode(isDialog()).assertDoesNotExist()
    }
    @Test fun scrimAndPredictiveReturnAnimateAndCancelledGestureRestoresTheSameWindow() {
        val shown = mutableStateOf(true)
        compose.setContent { MaterialTheme { if (shown.value) MotionAlertDialog({ shown.value = false },
            title = { Text("返回测试") }, confirmButton = { TextButton(dialogFeedbackClick { shown.value = false }) { Text("关闭") } }) } }
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        val container = compose.onNodeWithTag("alert-dialog-container"); val full = container.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            dialog.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_RIGHT))
            dialog.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(60f, 200f, .6f, BackEventCompat.EDGE_RIGHT))
        }; step(32)
        assertTrue(container.fetchSemanticsNode().boundsInRoot.height < full.height)
        compose.runOnIdle { dialog.onBackPressedDispatcher.dispatchOnBackCancelled() }; step(600)
        assertEquals(full, container.fetchSemanticsNode().boundsInRoot); assertTrue(shown.value)
        compose.onNodeWithTag("alert-dialog-scrim", useUnmergedTree = true).performTouchInput { click(topLeft + androidx.compose.ui.geometry.Offset(2f, 2f)) }
        step(64); assertTrue(shown.value); step(400); assertFalse(shown.value)
    }
}
