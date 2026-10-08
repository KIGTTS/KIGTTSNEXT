package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
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
class ActionMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun sameIdStyleLabelAndIconChangesAnimateAndInvokeOnlyTheCurrentAction() {
        var calls = 0
        var actions by mutableStateOf(listOf(ElasticAction("live", "旧入口", Icons.Default.Star, { calls += 100 })))
        compose.setContent { KigttsTheme(ThemeSettings(mode = ThemeMode.LIGHT)) {
            Box(Modifier.fillMaxSize()) {
                ElasticButtonGroup(actions, Modifier.widthIn(max = 240.dp).testTag("live-strip"))
            }
        } }
        val old = compose.onNodeWithTag("live").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { actions = listOf(ElasticAction("live", "新的文字按钮", Icons.Default.Edit, { calls++ }, iconOnly = false)) }
        compose.mainClock.advanceTimeBy(96); compose.waitForIdle()
        val mid = compose.onNodeWithTag("live").getUnclippedBoundsInRoot()
        compose.onRoot().captureRoboImage("build/previews/button-style-change-mid.png")
        compose.onNodeWithTag("live").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(1400); compose.waitForIdle()
        assertEquals(1, calls)
        val final = compose.onNodeWithTag("live").getUnclippedBoundsInRoot()
        assertTrue("Actual widths: old=" + old + " mid=" + mid + " final=" + final,
            (final.right - final.left).value > (old.right - old.left).value)
        compose.onNodeWithText("新的文字按钮").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/previews/button-style-change-complete.png")
        assertFalse(java.io.File("build/previews/button-style-change-mid.png").readBytes()
            .contentEquals(java.io.File("build/previews/button-style-change-complete.png").readBytes()))
        compose.runOnIdle { actions = emptyList() }
        compose.mainClock.advanceTimeBy(80); compose.waitForIdle()
        compose.onNodeWithTag("live").performClick()
        compose.onRoot().captureRoboImage("build/previews/button-remove-mid.png")
        compose.mainClock.advanceTimeBy(1400); compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("live").assertDoesNotExist()
        compose.onNodeWithText("新的文字按钮").assertDoesNotExist()
        assertEquals(1, calls)
    }
}
