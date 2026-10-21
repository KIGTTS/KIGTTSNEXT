package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
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
class CategoryInteractionTest {
    @Test fun swipingToAnOffscreenGroupScrollsItsSelectedTabIntoView() {
        val model=show()
        compose.runOnIdle {
            repeat(12) { model.quickTexts.addCategory("扩展分组$it") }
            model.category("扩展分组8")
        }
        compose.waitForIdle()
        val tab=compose.onNodeWithTag("category-扩展分组8");tab.assertIsDisplayed()
        compose.onNodeWithTag("category-content-transition").performTouchInput { swipeLeft() }
        compose.waitForIdle();assertEquals("扩展分组9",model.state.value.selectedCategory)
        val area=compose.onNodeWithTag("category-tabs").getUnclippedBoundsInRoot()
        val selected=compose.onNodeWithTag("category-扩展分组9").getUnclippedBoundsInRoot()
        assertTrue(selected.left>=area.left);assertTrue(selected.right<=area.right)
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val density get() = compose.activity.resources.displayMetrics.density
    private val ticks = mutableListOf<HapticFeedbackType>()
    private fun show(editing: Boolean = false): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "interaction", "quick-phrases").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { model ->
            val native = object : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { ticks += hapticFeedbackType } }
            compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides native) { KigttsApp(model) } }
            compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f); if (editing) model.editQuickTexts() }
            compose.waitForIdle()
        }
    }
    private fun point(node: SemanticsNodeInteraction, pane: SemanticsNodeInteraction): Offset {
        val child = node.getUnclippedBoundsInRoot(); val root = pane.getUnclippedBoundsInRoot()
        return Offset(((child.left.value + child.right.value) / 2 - root.left.value) * density,
            ((child.top.value + child.bottom.value) / 2 - root.top.value) * density)
    }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/" + name + ".png")
    @Test fun activeGroupShowsRightPencilAndSecondClickEditsNameAndIcon() {
        val model = show(true)
        val leading = compose.onNodeWithTag("category-icon-常用", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val pencil = compose.onNodeWithTag("category-grip-常用", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(pencil.left > leading.right)
        compose.onNodeWithContentDescription("编辑分组 常用", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("拖动分组 生活", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("category-生活").performClick()
        compose.onNodeWithTag("edit-category-name").assertDoesNotExist()
        compose.onNodeWithTag("category-生活").performTouchInput { click() }
        compose.onNodeWithTag("edit-category-name").performTextReplacement("常用")
        compose.onNodeWithTag("save-category").assertIsNotEnabled()
        compose.onNodeWithTag("edit-category-name").performTextReplacement("日常生活")
        compose.onNodeWithTag("choose-category-icon-HOME").performClick()
        compose.onNode(isDialog()).captureRoboImage("build/previews/edit-category-properties.png")
        compose.onNodeWithTag("save-category").performClick()
        compose.waitUntil(5000) { model.state.value.selectedCategory == "日常生活" }
        assertEquals("日常生活", model.state.value.selectedCategory)
        assertEquals(CategoryIcon.HOME, model.quickTexts.categoryIcons.value["日常生活"])
        compose.onNodeWithTag("category-日常生活").performClick()
        compose.onNodeWithTag("edit-category-name").performTextReplacement("不应保存")
        compose.onNodeWithText("取消").performClick()
        assertFalse("不应保存" in model.categories.value)
        assertEquals(model.categories.value, testAppModel(ApplicationProvider.getApplicationContext()).categories.value)
    }
    @Test fun groupAndWebEntryShareTheSearchableMaterialSymbolPicker() {
        val model = show(true)
        compose.onNodeWithTag("category-生活").performClick()
        compose.onNodeWithTag("category-生活").performTouchInput { click() }
        compose.onNodeWithTag("category-more-icons").performClick()
        compose.onNodeWithTag("symbol-search").performTextInput("two_wheeler")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("symbol/two_wheeler").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("symbol/two_wheeler").performClick()
        compose.onNodeWithTag("save-category").performClick()
        compose.waitUntil(5000) { model.quickTexts.categoryIcons.value["生活"]?.name == "symbol:two_wheeler" }
        assertEquals("symbol:two_wheeler", testAppModel(ApplicationProvider.getApplicationContext()).quickTexts.categoryIcons.value["生活"]?.name)
    }

    @Test fun diagonalGroupDragIsHorizontalOnlyWithSolidElevatedTab() {
        val model = show(true)
        val tabs = compose.onNodeWithTag("category-tabs")
        val from = point(compose.onNodeWithTag("category-grip-生活", useUnmergedTree = true), tabs)
        val target = point(compose.onNodeWithTag("category-常用"), tabs)
        val initial = compose.onNodeWithTag("category-生活").getUnclippedBoundsInRoot()
        ticks.clear()
        compose.mainClock.autoAdvance = false
        tabs.performTouchInput { down(from); moveTo(Offset(target.x, from.y + 120f * density), delayMillis = 300) }
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        val moving = compose.onNodeWithTag("category-生活").getUnclippedBoundsInRoot()
        assertEquals(1, ticks.count { it == HapticFeedbackType.GestureThresholdActivate })
        assertTrue(ticks.contains(HapticFeedbackType.SegmentFrequentTick))
        assertEquals(initial.top.value, moving.top.value, .5f)
        compose.onNodeWithTag("category-生活").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在拖动分组"))
        capture("category-horizontal-drag-shadow")
        tabs.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1200); compose.mainClock.autoAdvance = true
        assertEquals(listOf("生活", "常用", "收藏", "出行"), model.categories.value)
        assertEquals(1, ticks.count { it == HapticFeedbackType.GestureEnd })
        compose.onNodeWithTag("edit-category-name").assertDoesNotExist()
    }
    @Test fun edgeDwellTransfersTwiceWithoutInterruptingThePointerOrLosingIdentity() {
        val model = show(true)
        val original = model.phrases.value.first()
        val pane = compose.onNodeWithTag("quick-editor-pane")
        val from = point(compose.onNodeWithTag("phrase-grip-" + original.id, useUnmergedTree = true), pane)
        compose.mainClock.autoAdvance = false
        pane.performTouchInput { down(from); moveTo(Offset(width - 4f * density, from.y + 20f * density), delayMillis = 150) }
        compose.mainClock.advanceTimeBy(160); compose.waitForIdle()
        compose.onNodeWithTag("phrase-edge-hint").assertExists()
        capture("phrase-cross-group-edge-hint")
        compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
        assertEquals("生活", model.state.value.selectedCategory)
        assertEquals("生活", model.phrases.value.first { it.id == original.id }.category)
        capture("phrase-cross-group-drag-shadow")
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        assertEquals("生活", model.state.value.selectedCategory)
        pane.performTouchInput { moveTo(Offset(width / 2f, from.y + 20f * density), delayMillis = 100) }
        compose.mainClock.advanceTimeBy(32); compose.waitForIdle()
        pane.performTouchInput { moveTo(Offset(4f * density, from.y + 20f * density), delayMillis = 100) }
        compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
        assertEquals("常用", model.state.value.selectedCategory)
        val target = model.phrases.value.filter { it.category == "常用" }.first()
        val to = point(compose.onNodeWithTag("edit-phrase-" + target.id), pane)
        pane.performTouchInput { moveTo(to, delayMillis = 100); up() }
        compose.mainClock.advanceTimeBy(1500); compose.mainClock.autoAdvance = true
        assertEquals(original.id, model.phrases.value.filter { it.category == "常用" }.first().id)
        assertEquals(1, model.phrases.value.count { it.id == original.id })
        assertEquals(model.phrases.value, testAppModel(ApplicationProvider.getApplicationContext()).phrases.value)
        compose.onNodeWithTag("phrase-detail-text").assertDoesNotExist()
        compose.onNodeWithTag("dragged-phrase").assertDoesNotExist()
        val next = model.phrases.value.filter { it.category == "常用" }[1]
        val handle = point(compose.onNodeWithTag("phrase-grip-" + original.id, useUnmergedTree = true), pane)
        val second = point(compose.onNodeWithTag("edit-phrase-" + next.id), pane)
        pane.performTouchInput { down(handle); moveTo(second, delayMillis = 150); up() }
        assertEquals(original.id, model.phrases.value.filter { it.category == "常用" }[1].id)
    }
    @Test fun normalAndEditingGridsSwipeGroupsWithSlidesButSingleRowOnlyScrollsItems() {
        val model = show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("phrases-vertical").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(112); compose.waitForIdle()
        assertEquals("生活", model.state.value.selectedCategory)
        capture("category-slide-next-mid")
        compose.mainClock.advanceTimeBy(1200); compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("category-content-transition").performTouchInput { swipeRight() }
        assertEquals("常用", model.state.value.selectedCategory)
        compose.runOnIdle { model.panelHeight(184f) }
        compose.onNodeWithTag("phrases-horizontal").performTouchInput { swipeLeft() }
        assertEquals("常用", model.state.value.selectedCategory)
        compose.runOnIdle { model.editQuickTexts() }
        compose.onNodeWithTag("editable-phrases").performTouchInput { swipeLeft() }
        assertEquals("生活", model.state.value.selectedCategory)
        compose.onNodeWithTag("editable-phrases").performTouchInput { swipeRight() }
        assertEquals("常用", model.state.value.selectedCategory)
    }
}
