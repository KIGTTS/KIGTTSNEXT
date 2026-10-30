package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.*
import com.kigtts.next.ui.management.ManagedItemList
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class SwipeListInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val pulses = mutableListOf<HapticFeedbackType>()
    private val calls = mutableListOf<String>()
    private fun show(reorder: Boolean = false) {
        val haptics = object : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { pulses += hapticFeedbackType } }
        compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides haptics) { KigttsTheme(ThemeSettings()) {
            ManagedItemList(ManagementList("swipes", (1..3).map { ManagementItem("$it", "条目 $it", primaryAction = "open",
                actions = listOf(PageAction("pin", "置顶", PluginIcon.PIN), PageAction("info", "关于", PluginIcon.INFO), PageAction("delete", "删除", symbol = "delete"))) },
                reorderAction = if (reorder) "order" else null), invoke = { action, _ -> calls += action })
        } } }
    }
    @Test fun pinRequiresFullDistanceAndPhysicalReleaseAndPulsesAtTheEdge() {
        show()
        val row = compose.onNodeWithTag("reveal-row/2")
        row.performTouchInput { down(Offset(5f, height / 2f)); moveTo(Offset(width - 2f, height / 2f), 400) }
        compose.waitForIdle()
        assertTrue(calls.isEmpty())
        assertEquals(1, pulses.count { it == HapticFeedbackType.GestureThresholdActivate })
        row.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf("pin"), calls)
        compose.onNodeWithTag("reveal-action/2/pin").assertDoesNotExist()
    }
    @Test fun backingAwayFromTheEdgeOrCancellingDoesNotPin() {
        show()
        val row = compose.onNodeWithTag("reveal-row/2")
        row.performTouchInput {
            down(Offset(5f, height / 2f)); moveTo(Offset(width - 2f, height / 2f), 400)
            moveTo(Offset(30f, height / 2f), 200); up()
        }
        compose.waitForIdle(); assertTrue(calls.isEmpty())
        row.performTouchInput { down(Offset(5f, height / 2f)); moveTo(Offset(width - 2f, height / 2f), 400); cancel() }
        compose.waitForIdle(); assertTrue(calls.isEmpty())
    }
    @Test fun leftEdgePulsesAndShowsEqualHeightCapsulesWithoutDeleting() {
        show()
        val row = compose.onNodeWithTag("reveal-row/2")
        row.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue(calls.isEmpty())
        assertEquals(1, pulses.count { it == HapticFeedbackType.GestureThresholdActivate })
        val bounds = compose.onNodeWithTag("managed-item/2").getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag("reveal-action/2/delete").getUnclippedBoundsInRoot()
        assertEquals((bounds.bottom - bounds.top).value, (button.bottom - button.top).value, .5f)
    }
    @Test fun longPressOpensLeftMenuInsteadOfStartingReorderOrSelecting() {
        show(reorder = true)
        compose.onNodeWithText("条目 2").performTouchInput { longClick() }
        compose.onNodeWithTag("reveal-action/2/delete").assertIsDisplayed()
        compose.onNodeWithTag("managed-drag/2").assertDoesNotExist()
        assertTrue(calls.isEmpty())
    }
    @Test fun dragReleasePulsePrecedesSettlingAndIsNotRepeatedAfterDrop() {
        show(reorder = true)
        compose.mainClock.autoAdvance = false
        val list = compose.onNodeWithTag("managed-list/swipes")
        val root = list.fetchSemanticsNode().boundsInRoot
        val grip = compose.onNodeWithTag("managed-grip/2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - root.topLeft
        list.performTouchInput { down(grip); moveBy(Offset(0f, 50f), 100) }
        compose.mainClock.advanceTimeBy(48)
        assertEquals(0, pulses.count { it == HapticFeedbackType.GestureEnd })
        list.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, pulses.count { it == HapticFeedbackType.GestureEnd }) }
        compose.onNodeWithTag("managed-drag/2").assertExists()
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("managed-drag/2").assertDoesNotExist()
        assertEquals(1, pulses.count { it == HapticFeedbackType.GestureEnd })
    }
}
