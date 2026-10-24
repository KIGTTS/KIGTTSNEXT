package com.kigtts.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.components.ProvideButtonFeedback
import com.kigtts.next.ui.management.ManagedItemList
import com.kigtts.plugin.api.*
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
class ManagedItemListTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun confirmingAnItemDoesNotShiftTheListWhenBusyChanges() {
        var busy by mutableStateOf(false)
        compose.setContent { KigttsTheme(ThemeSettings()) { ProvideButtonFeedback(true) {
            ManagedItemList(ManagementList("busy", listOf(ManagementItem("a", "语音包", primaryAction = "select"))),
                busy = busy, invoke = { _, _ -> busy = true })
        } } }
        val row = compose.onNodeWithTag("managed-item/a")
        val before = row.getUnclippedBoundsInRoot()
        row.performClick()
        assertEquals("The progress indicator occupies no list space", before, row.getUnclippedBoundsInRoot())
        compose.runOnIdle { busy = false }
        assertEquals(before, row.getUnclippedBoundsInRoot())
    }
    @Test fun dragKeepsCardDimensionsAndCommitsOneOrderAfterAnimatedDrop() {
        var values by mutableStateOf(listOf("a", "b", "c")); val commits = mutableListOf<List<String>>()
        compose.setContent { KigttsTheme(ThemeSettings()) { ProvideButtonFeedback(true) {
            ManagedItemList(ManagementList("test", values.map { ManagementItem(it, "条目 $it", "备注", toggle = ManagementToggle(true, "toggle")) }, "order"), invoke = { action, inputs ->
                if (action == "order") { values = PluginJson.decodeFromString(inputs.getValue("order")); commits += values }
            })
        } } }
        compose.mainClock.autoAdvance = false
        val density = compose.activity.resources.displayMetrics.density
        val list = compose.onNodeWithTag("managed-list/test")
        val root = list.getUnclippedBoundsInRoot()
        val original = compose.onNodeWithTag("managed-item/b").getUnclippedBoundsInRoot()
        val grip = compose.onNodeWithTag("managed-grip/b", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val start = Offset(((grip.left.value + grip.right.value) / 2 - root.left.value) * density, ((grip.top.value + grip.bottom.value) / 2 - root.top.value) * density)
        list.performTouchInput { down(start); moveBy(Offset(0f, 24f * density), 100) }
        compose.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(160); compose.waitForIdle()
        val floating = compose.onNodeWithTag("managed-drag/b").getUnclippedBoundsInRoot()
        assertEquals((original.right - original.left).value, (floating.right - floating.left).value, .5f)
        assertEquals((original.bottom - original.top).value, (floating.bottom - floating.top).value, .5f)
        assertEquals("Picking up a row must preserve its horizontal position", original.left.value, floating.left.value, .5f)
        assertEquals("Pickup follows the finger without adding content padding", original.top.value + 24f, floating.top.value, .5f)
        assertTrue(commits.isEmpty())
        val target = compose.onNodeWithTag("managed-item/c").getUnclippedBoundsInRoot()
        list.performTouchInput { moveTo(Offset(start.x, ((target.top.value + target.bottom.value) / 2 - root.top.value) * density), 150); up() }
        compose.mainClock.advanceTimeBy(1800); compose.waitForIdle()
        assertEquals(listOf(listOf("a", "c", "b")), commits)
        compose.onNodeWithTag("managed-drag/b").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/previews/managed-list-after-reorder.png")
    }
    @Test fun rowHeightStaysCompactAndPinnedGroupCannotMoveIntoOtherGroup() {
        val values = listOf(ManagementItem("p", "置顶", pinned = true, group = "pin"), ManagementItem("n", "普通", group = "normal"))
        val orders = mutableListOf<String>()
        compose.setContent { KigttsTheme(ThemeSettings()) { ProvideButtonFeedback(true) {
            ManagedItemList(ManagementList("groups", values, "order"), invoke = { _, inputs -> orders += inputs.getValue("order") })
        } } }
        val bounds = compose.onNodeWithTag("managed-item/p").getUnclippedBoundsInRoot()
        assertEquals(80f, (bounds.bottom - bounds.top).value, 1f)
        val action = compose.onNodeWithTag("managed-grip/p", useUnmergedTree = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].single { it.label == "下移" }
        compose.runOnIdle { action.action() }
        assertTrue(orders.isEmpty())
    }
}
