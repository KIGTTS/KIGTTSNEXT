package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpOffset
import com.kigtts.next.ui.management.InlineProgressButton
import com.kigtts.plugin.api.ManagementProgressAction
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

private val DpRect.width get() = right - left
private val DpRect.height get() = bottom - top
private val DpRect.center get() = DpOffset((left + right) / 2, (top + bottom) / 2)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class InlineProgressButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun capsuleAndCircleKeepTheSameHeightAndCenterThroughBothDirections() {
        var state by mutableStateOf(ManagementProgressAction("获取", "get"))
        val calls = mutableListOf<String>()
        compose.setContent { MaterialTheme { InlineProgressButton("font", state, true) { action, _ -> calls += action } } }
        compose.mainClock.autoAdvance = false
        val target = compose.onNodeWithTag("managed-action/font")
        val shape = compose.onNodeWithTag("managed-action-shape/font", useUnmergedTree = true)
        val idle = shape.getUnclippedBoundsInRoot()
        assertEquals(36f, idle.height.value, .1f)
        target.performClick()
        compose.runOnIdle { state = ManagementProgressAction("取消下载", "cancel", .4f, true) }
        compose.mainClock.advanceTimeBy(16)
        var previous = idle.width.value
        repeat(4) {
            compose.mainClock.advanceTimeBy(48)
            compose.waitForIdle()
            val bounds = shape.getUnclippedBoundsInRoot()
            assertEquals(idle.center.x.value, bounds.center.x.value, .2f) // Half-pixel layout rounding at 3x density.
            assertEquals(idle.top.value, bounds.top.value, .1f)
            assertEquals(36f, bounds.height.value, .1f)
            assertTrue(bounds.width.value <= previous + .1f)
            previous = bounds.width.value
        }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        assertEquals(36f, shape.getUnclippedBoundsInRoot().width.value, .1f)
        target.performClick()
        compose.runOnIdle { state = ManagementProgressAction("安装", "install") }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        val middle = shape.getUnclippedBoundsInRoot()
        assertTrue(middle.width.value in 37f..63f)
        assertEquals(36f, middle.height.value, .1f)
        assertEquals(idle.center.x.value, middle.center.x.value, .2f)
        target.captureRoboImage("build/previews/download-button-expanding.png")
        compose.mainClock.advanceTimeBy(300)
        assertEquals(idle.width.value, shape.getUnclippedBoundsInRoot().width.value, .1f)
        target.performClick()
        compose.runOnIdle { assertEquals(listOf("get", "cancel", "install"), calls) }
    }

    @Test fun reversingMidMorphStartsAtTheCurrentWidth() {
        var running by mutableStateOf(false)
        compose.setContent { MaterialTheme {
            InlineProgressButton("reverse", ManagementProgressAction(if (running) "取消" else "获取", "action", .2f, running), true) { _, _ -> }
        } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { running = true }
        compose.mainClock.advanceTimeBy(96)
        val shape = compose.onNodeWithTag("managed-action-shape/reverse", useUnmergedTree = true)
        val before = shape.getUnclippedBoundsInRoot()
        compose.runOnIdle { running = false }
        compose.mainClock.advanceTimeBy(16)
        val after = shape.getUnclippedBoundsInRoot()
        assertEquals(before.width.value, after.width.value, 4f)
        assertEquals(before.center.x.value, after.center.x.value, .2f)
        compose.mainClock.advanceTimeBy(300)
        assertEquals(64f, shape.getUnclippedBoundsInRoot().width.value, .1f)
    }
}
