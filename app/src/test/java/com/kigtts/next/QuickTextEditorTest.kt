package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
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
class QuickTextEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "ui-debug", "home-panel", "voice-input", "quick-phrases").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        QuickPhraseStore(app).save(listOf(QuickPhrase("第一条", "常用"), QuickPhrase("第二条", "常用"),
            QuickPhrase("第三条", "常用")), listOf("常用", "生活", "收藏", "出行"))
        return testAppModel(app).also { model ->
            compose.setContent { KigttsApp(model) }
            compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f) }
        }
    }
    private fun edit() = compose.onNodeWithContentDescription("编辑快捷文本").performClick()
    @Test fun toolbarShowsCurrentGroupAndAddsToItWithoutReplacingTailAction() {
        val model = show(); edit()
        compose.runOnIdle { model.category("生活") }
        val group = compose.onNodeWithTag("quick-edit-group")
        group.assertTextContains("生活")
        group.performClick()
        compose.onNodeWithTag("edit-category-name").assertTextContains("生活")
        compose.onNodeWithText("取消").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("edit-category-name").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("add-phrase").assertExists()
        compose.onNodeWithTag("quick-edit-add").performClick()
        assertEquals("生活", model.content.phraseDraft.value?.category)
    }
    private fun capture(name: String) {
        val target = if (compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) compose.onNode(isDialog()) else compose.onRoot()
        target.captureRoboImage("build/previews/" + name + ".png")
    }
    private fun point(node: SemanticsNodeInteraction, container: SemanticsNodeInteraction): Offset {
        val child = node.getUnclippedBoundsInRoot(); val parent = container.getUnclippedBoundsInRoot()
        val scale = compose.activity.resources.displayMetrics.density
        return Offset(((child.left.value + child.right.value) / 2 - parent.left.value) * scale,
            ((child.top.value + child.bottom.value) / 2 - parent.top.value) * scale)
    }
    @Test fun editorFillsThePageAndPredictiveReturnRestoresTheOriginalPanels() {
        val model = show()
        compose.runOnIdle { model.toggleListening() }
        edit()
        compose.onNodeWithTag("editor-area").assertDoesNotExist()
        compose.onNodeWithTag("captions").assertDoesNotExist()
        compose.onNodeWithContentDescription("KIGTTS 横版 Logo").assertDoesNotExist()
        assertEquals(compose.onRoot().getUnclippedBoundsInRoot(), compose.onNodeWithTag("quick-text-editor").getUnclippedBoundsInRoot())
        capture("quick-edit-fullscreen")
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, 0)); dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, .6f, 0)) }
        capture("quick-edit-predictive")
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        assertTrue(model.state.value.quickTextEditing)
        compose.runOnIdle { dispatcher.onBackPressed() }
        assertFalse(model.state.value.quickTextEditing)
        assertEquals(340f, model.state.value.panelHeight)
        assertTrue(model.state.value.captionsVisible)
        compose.onNodeWithTag("phrases-vertical").assertIsDisplayed()
    }
    @Test fun deleteRequiresConfirmationAndCancelKeepsTheItem() {
        val model = show(); edit()
        val item = model.phrases.value.first()
        val count = model.phrases.value.size
        compose.onNodeWithTag("delete-phrase-" + item.id).performClick()
        assertEquals(count, model.phrases.value.size)
        capture("quick-edit-delete-confirm")
        compose.onNodeWithTag("cancel-delete-phrase").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("cancel-delete-phrase").fetchSemanticsNodes().isEmpty() }
        assertEquals(count, model.phrases.value.size)
        compose.onNodeWithTag("delete-phrase-" + item.id).performClick()
        compose.onNodeWithTag("confirm-delete-phrase").performClick()
        compose.waitUntil(5000) { model.phrases.value.none { it.id == item.id } }
        assertFalse(model.phrases.value.any { it.id == item.id })
        assertEquals(count - 1, model.phrases.value.size)
    }
    @Test fun tappingDetailsAndTailAdditionsPersistContentTitleAndGroups() {
        val model = show(); edit()
        val item = model.phrases.value.first()
        compose.onNodeWithTag("edit-phrase-" + item.id).performClick()
        compose.onNodeWithTag("phrase-detail-text").performTextReplacement("修改条目")
        compose.onNodeWithTag("phrase-detail-title").performTextInput("缩略标题")
        capture("quick-edit-details")
        compose.onNodeWithTag("save-phrase-details").performClick()
        compose.waitUntil(5000) { model.content.phraseDraft.value == null }
        assertEquals("修改条目", model.phrases.value.first { it.id == item.id }.text)
        compose.onNodeWithTag("add-phrase").performClick()
        compose.onNodeWithTag("phrase-detail-text").performTextInput("新条目")
        compose.onNodeWithTag("save-phrase-details").performClick()
        compose.waitUntil(5000) { model.content.phraseDraft.value == null }
        assertEquals("新条目", model.phrases.value.last().text)
        compose.onNodeWithTag("category-tabs").performScrollToNode(hasTestTag("add-category"))
        compose.onNodeWithTag("add-category").performClick()
        compose.onNodeWithTag("new-category-name").performTextInput("新分组")
        compose.onNodeWithTag("save-category").performClick()
        compose.waitUntil(5000) { model.state.value.selectedCategory == "新分组" }
        assertEquals("新分组", model.categories.value.last())
        assertEquals("新分组", model.state.value.selectedCategory)
        compose.onNodeWithTag("add-phrase").performClick()
        compose.onNodeWithTag("phrase-detail-text").performTextInput("组内文本")
        compose.onNodeWithTag("save-phrase-details").performClick()
        compose.waitUntil(5000) { model.content.phraseDraft.value == null }
        val restored = testAppModel(ApplicationProvider.getApplicationContext())
        assertEquals(model.phrases.value, restored.phrases.value)
        assertEquals(model.categories.value, restored.categories.value)
        assertEquals("缩略标题", restored.phrases.value.first { it.id == item.id }.title)
        capture("quick-edit-custom-group")
    }
    @Test fun itemHandleAndLongPressDragReorderWithoutOpeningDetails() {
        val model = show(); edit()
        val original = model.phrases.value.filter { it.category == "常用" }
        val grid = compose.onNodeWithTag("editable-phrases")
        val from = point(compose.onNodeWithTag("phrase-grip-" + original[0].id, useUnmergedTree = true), grid)
        val to = point(compose.onNodeWithTag("edit-phrase-" + original[2].id), grid)
        grid.performTouchInput { down(from); moveTo(to, delayMillis = 300); up() }
        assertEquals(original[0].id, model.phrases.value.filter { it.category == "常用" }[2].id)
        compose.onNodeWithTag("phrase-detail-text").assertDoesNotExist()
        val item = model.phrases.value.first()
        val start = point(compose.onNodeWithTag("edit-phrase-" + item.id), grid)
        val end = point(compose.onNodeWithTag("edit-phrase-" + original[0].id), grid)
        grid.performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(700)
        grid.performTouchInput { moveTo(end, delayMillis = 300); up() }
        assertEquals(item.id, model.phrases.value.filter { it.category == "常用" }[2].id)
        compose.onNodeWithTag("phrase-detail-text").assertDoesNotExist()
        capture("quick-edit-reordered-items")
    }
    @Test fun groupHandlesAndLongPressSupportHorizontalReordering() {
        val model = show(); edit()
        val tabs = compose.onNodeWithTag("category-tabs")
        val from = point(compose.onNodeWithTag("category-grip-常用", useUnmergedTree = true), tabs)
        val to = point(compose.onNodeWithTag("category-生活"), tabs)
        tabs.performTouchInput { down(from); moveTo(to, delayMillis = 300); up() }
        assertEquals(listOf("生活", "常用", "收藏", "出行"), model.categories.value)
        compose.onNodeWithTag("category-生活").assertIsDisplayed()
        val start = point(compose.onNodeWithTag("category-生活"), tabs)
        val end = point(compose.onNodeWithTag("category-常用"), tabs)
        tabs.performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(700)
        tabs.performTouchInput { moveTo(end, delayMillis = 300) }
        compose.onNodeWithTag("category-生活").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在拖动分组"))
        tabs.performTouchInput { up() }
        assertEquals(listOf("常用", "生活", "收藏", "出行"), model.categories.value)
        assertEquals("常用", model.state.value.selectedCategory)
        capture("quick-edit-reordered-groups")
    }
    @Test fun groupReleaseReturnsThroughIntermediatePositionsInsteadOfTeleporting() {
        show(); edit()
        val tabs = compose.onNodeWithTag("category-tabs")
        val picked = compose.onNodeWithTag("category-常用")
        val from = point(compose.onNodeWithTag("category-grip-常用", useUnmergedTree = true), tabs)
        val to = point(compose.onNodeWithTag("category-生活"), tabs)
        compose.mainClock.autoAdvance = false
        tabs.performTouchInput { down(from); moveTo(to + Offset(18f * compose.activity.resources.displayMetrics.density, 0f), delayMillis = 300) }
        compose.mainClock.advanceTimeBy(160); compose.waitForIdle()
        val before = picked.getUnclippedBoundsInRoot()
        tabs.performTouchInput { up() }; compose.waitForIdle()
        val released = picked.getUnclippedBoundsInRoot()
        assertEquals("Release keeps the floating pose", before.left.value, released.left.value, 1f)
        compose.mainClock.advanceTimeBy(96); compose.waitForIdle()
        val middle = picked.getUnclippedBoundsInRoot()
        compose.mainClock.advanceTimeBy(1600); compose.waitForIdle()
        val final = picked.getUnclippedBoundsInRoot()
        assertTrue("Drop has intermediate motion", kotlin.math.abs(middle.left.value - final.left.value) > .5f)
        assertTrue(kotlin.math.abs(middle.left.value - final.left.value) < kotlin.math.abs(released.left.value - final.left.value))
        compose.mainClock.autoAdvance = true
    }
    @Test fun holdingAnItemAtTheEdgeScrollsToFurtherRowsAndKeepsReordering() {
        val model = show()
        compose.runOnIdle { repeat(24) { model.addPhrase("长列表条目 " + it, "常用") } }
        edit()
        val item = model.phrases.value.first()
        val grid = compose.onNodeWithTag("editable-phrases")
        val from = point(compose.onNodeWithTag("phrase-grip-" + item.id, useUnmergedTree = true), grid)
        compose.mainClock.autoAdvance = false
        grid.performTouchInput { down(from); moveTo(Offset(from.x, height - 12f), delayMillis = 300) }
        repeat(24) { compose.mainClock.advanceTimeBy(32); compose.waitForIdle() }
        val scroll = grid.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(scroll > 0f)
        assertTrue(model.phrases.value.filter { it.category == "常用" }.indexOfFirst { it.id == item.id } > 8)
        grid.captureRoboImage("build/previews/quick-edit-drag-auto-scroll.png")
        grid.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500); compose.mainClock.autoAdvance = true
        assertEquals(model.phrases.value, testAppModel(ApplicationProvider.getApplicationContext()).phrases.value)
    }
    @Test fun inlineGripKeepsRowsCompactAndDeleteStaysCircularWhilePressed() {
        val model = show()
        val normal = compose.onNodeWithText(model.phrases.value.first().text).getUnclippedBoundsInRoot()
        compose.runOnIdle { model.quickTexts.edit(model.phrases.value.first().id, "带说明的条目", "保留辅助说明", "常用") }
        edit()
        val item = model.phrases.value.first()
        val tile = compose.onNodeWithTag("edit-phrase-" + item.id).getUnclippedBoundsInRoot()
        val grip = compose.onNodeWithTag("phrase-grip-" + item.id, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val deletion = compose.onNodeWithTag("delete-phrase-" + item.id).getUnclippedBoundsInRoot()
        val tileHeight = (tile.bottom - tile.top).value
        assertEquals(88f, tileHeight, .5f)
        assertEquals((normal.bottom - normal.top).value, tileHeight, .5f)
        assertTrue("Grip shares the content row", kotlin.math.abs((grip.top.value + grip.bottom.value - tile.top.value - tile.bottom.value) / 2) < 1f)
        assertEquals(32f, (deletion.right - deletion.left).value, .5f)
        assertEquals((deletion.right - deletion.left).value, (deletion.bottom - deletion.top).value, .5f)
        capture("quick-edit-compact")
        fun circle(name: String) {
            val path = "build/previews/" + name + ".png"
            compose.onNodeWithTag("delete-phrase-" + item.id).captureRoboImage(path)
            val image = BitmapFactory.decodeFile(path)
            val x = (image.width * .12f).toInt(); val y = (image.height * .12f).toInt()
            assertEquals(image.width, image.height)
            assertNotEquals("Circle leaves its corners unfilled", image.getPixel(x, y), image.getPixel(x, image.height / 2))
        }
        circle("quick-delete-circle")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("delete-phrase-" + item.id).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160); compose.waitForIdle()
        circle("quick-delete-circle-pressed")
        val pressedTile = compose.onNodeWithTag("edit-phrase-" + item.id).getUnclippedBoundsInRoot()
        assertEquals(tileHeight, (pressedTile.bottom - pressedTile.top).value, .5f)
        compose.onNodeWithTag("delete-phrase-" + item.id).performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("confirm-delete-phrase").assertDoesNotExist()
    }
    @Test fun newGroupIconCanBeSelectedCancelledAndPersistsInBothTabModes() {
        val model = show(); edit()
        fun addGroup() {
            compose.onNodeWithTag("category-tabs").performScrollToNode(hasTestTag("add-category"))
            compose.onNodeWithTag("add-category").performClick()
        }
        addGroup()
        compose.onNodeWithTag("save-category").assertIsNotEnabled()
        compose.onNodeWithTag("choose-category-icon-SHOPPING").performClick().assertIsSelected()
        compose.onNodeWithText("取消").performClick()
        assertEquals(4, model.categories.value.size)
        addGroup()
        compose.onNodeWithTag("new-category-name").performTextInput("购物交流")
        compose.onNodeWithTag("choose-category-icon-SHOPPING").performClick()
        capture("new-category-icons")
        compose.onNodeWithTag("save-category").performClick()
        assertEquals(CategoryIcon.SHOPPING, model.quickTexts.categoryIcons.value["购物交流"])
        compose.onNodeWithTag("category-tabs").performScrollToNode(hasTestTag("category-购物交流"))
        compose.onNodeWithTag("category-icon-购物交流", useUnmergedTree = true).assertContentDescriptionEquals("分组图标：购物")
        compose.onNodeWithContentDescription("编辑分组 购物交流", useUnmergedTree = true).assertExists()
        capture("quick-edit-group-icon")
        compose.onNodeWithTag("quick-edit-done").performClick()
        compose.onNodeWithTag("category-tabs").performScrollToNode(hasTestTag("category-购物交流"))
        compose.onNodeWithTag("category-icon-购物交流", useUnmergedTree = true).assertContentDescriptionEquals("分组图标：购物")
        val restored = testAppModel(ApplicationProvider.getApplicationContext())
        assertEquals(model.quickTexts.categoryIcons.value, restored.quickTexts.categoryIcons.value)
        capture("quick-group-icon")
    }
}
