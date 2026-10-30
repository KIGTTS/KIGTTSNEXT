package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.next.ui.home.QuickTextContent
import com.kigtts.next.ui.home.QuickPhraseDragOverlay
import com.kigtts.next.ui.components.reorder.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class QuickSortUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun heldCardCornersFollowItsNewPositionBeforeTheFingerIsReleased() {
        val model = testAppModel(ApplicationProvider.getApplicationContext<Application>())
        compose.activity.viewModelStore.put("quick-corners", model)
        val first = QuickPhrase("你好", "分组", id = "first")
        var phrases by mutableStateOf(listOf(first, QuickPhrase("第二条", "分组"), QuickPhrase("第三条", "分组")))
        val drag = ReorderDrag({ emptyList() }, { _, _ -> })
        drag.begin(ReorderTarget(first.id, Rect(0f, 0f, 180f, 80f)), Offset(90f, 40f))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme { Box(Modifier.size(200.dp, 100.dp).background(Color.Magenta).testTag("corner-stage")) {
                    QuickPhraseDragOverlay(drag, phrases, true, 1, model)
                } }
            }
        }
        fun cornerGreen(): Int {
            val file = java.io.File("build/previews/quick-corners.png")
            compose.onNodeWithTag("corner-stage").captureRoboImage(file.path)
            val bitmap = requireNotNull(android.graphics.BitmapFactory.decodeFile(file.path))
            return try { android.graphics.Color.green(bitmap.getPixel(3, 3)) } finally { bitmap.recycle() }
        }
        assertTrue(cornerGreen() < 128)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { phrases = listOf(phrases[1], first, phrases[2]) }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        assertEquals(first.id, drag.key)
        assertTrue("The held card takes its new connected corner without waiting for drop", cornerGreen() > 200)
    }
    @Test fun singleRowKeepsTheGrabbedFirstCardUnderTheFingerAfterSwapping() = verifyLeadingCardDrag(true)
    @Test fun singleColumnKeepsTheGrabbedFirstCardUnderTheFingerAfterSwapping() = verifyLeadingCardDrag(false)
    @Test fun quickSortingCanMoveAcrossGroupsFromTheGridEdge() = verifyEdgeTransfer(false, 2)
    @Test fun singleColumnStillAllowsEdgeTransfer() = verifyEdgeTransfer(false, 1)
    @Test fun horizontalSingleRowKeepsEdgeDragsInsideItsOwnGroup() = verifyEdgeTransfer(true, 1)
    private fun verifyEdgeTransfer(singleRow: Boolean, columns: Int) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("interaction", "quick-phrases", "home-panel").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        val model = testAppModel(app)
        model.quickSort.choose(true)
        val category = model.categories.value.first()
        val destination = model.categories.value[1]
        model.category(category)
        val phrase = model.phrases.value.first { it.category == category }
        compose.setContent {
            val state by model.state.collectAsState(); val phrases by model.phrases.collectAsState(); val categories by model.categories.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme { QuickTextContent(state, phrases, categories, model, singleRow, columns, Modifier.width(350.dp).height(500.dp)) }
            }
        }
        val pane = compose.onNodeWithTag("quick-sort-pane")
        val root = pane.fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag("quick-phrase-${phrase.id}").fetchSemanticsNode().boundsInRoot.center - root.topLeft
        compose.mainClock.autoAdvance = false
        pane.performTouchInput { down(start); advanceEventTime(700); moveTo(Offset(width - 2f, start.y), 150) }
        compose.mainClock.advanceTimeBy(700); compose.waitForIdle()
        if (singleRow) {
            assertEquals(category, model.state.value.selectedCategory)
            assertEquals(category, model.phrases.value.single { it.id == phrase.id }.category)
            compose.onNodeWithTag("phrase-edge-hint").assertDoesNotExist()
        } else {
            assertEquals(destination, model.state.value.selectedCategory)
            assertEquals(destination, model.phrases.value.single { it.id == phrase.id }.category)
            val held = compose.onNodeWithTag("quick-phrase-${phrase.id}").fetchSemanticsNode().boundsInRoot
            assertEquals(root.right - 2f, held.center.x, 2f)
            assertEquals(root.top + start.y, held.center.y, 2f)
        }
        pane.performTouchInput { up() }
        compose.mainClock.autoAdvance = true; compose.waitForIdle()
        assertEquals("", model.state.value.editorText)
    }
    private fun verifyLeadingCardDrag(horizontal: Boolean) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("interaction", "quick-phrases", "home-panel").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        val model = testAppModel(app)
        model.quickSort.choose(true)
        val category = model.state.value.selectedCategory
        repeat(8) { model.quickTexts.savePhrase(QuickPhrase("排序 $it", category)) }
        compose.setContent {
            val state by model.state.collectAsState(); val phrases by model.phrases.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val categories by model.categories.collectAsState()
                MaterialTheme { QuickTextContent(state, phrases, categories, model, horizontal, 1, Modifier.width(350.dp).height(500.dp)) }
            }
        }
        val items = model.phrases.value.filter { it.category == category }
        val container = compose.onNodeWithTag(if (horizontal) "phrases-horizontal" else "phrases-vertical")
        fun bounds(id: String) = compose.onNodeWithTag("quick-phrase-$id").fetchSemanticsNode().boundsInRoot
        val initial = bounds(items[0].id)
        val destination = bounds(items[1].id).center
        val root = container.fetchSemanticsNode().boundsInRoot.topLeft
        compose.mainClock.autoAdvance = false
        container.performTouchInput { down(initial.center - root); advanceEventTime(700); moveTo(destination - root, 100) }
        repeat(5) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        assertEquals(items[0].id, model.phrases.value.filter { it.category == category }[1].id)
        val held = bounds(items[0].id)
        assertEquals("Dragged card must remain beneath the finger after the first item changes", destination.x, held.center.x, 2f)
        assertEquals(destination.y, held.center.y, 2f)
        val afterSwap = model.phrases.value.map { it.id }
        repeat(12) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        assertEquals(afterSwap, model.phrases.value.map { it.id })
        assertEquals(held.center.x, bounds(items[0].id).center.x, 2f)
        assertEquals(held.center.y, bounds(items[0].id).center.y, 2f)
        container.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("", model.state.value.editorText)
    }
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("interaction", "quick-phrases", "home-panel").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { model ->
            compose.setContent { KigttsApp(model) }
            compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.panelHeight(340f) }
        }
    }
    private fun center(tag: String, container: String): Offset {
        val child = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val parent = compose.onNodeWithTag(container).getUnclippedBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        return Offset(((child.left + child.right) / 2 - parent.left).value * density,
            ((child.top + child.bottom) / 2 - parent.top).value * density)
    }
    @Test fun firstHoldAsksOnceAndDoesNotSendOrSortWhenDeclined() {
        val model = show(); val before = model.phrases.value; val item = before.first()
        compose.onNodeWithTag("quick-phrase-${item.id}").performTouchInput { longClick() }
        compose.onNodeWithText("开启快速排序？").assertIsDisplayed()
        compose.onNodeWithText("暂不开启").performClick()
        compose.waitUntil(5000) { model.quickSort.enabled.value != null }
        assertFalse(model.quickSort.enabled.value!!); assertEquals("", model.state.value.editorText)
        compose.onNodeWithTag("quick-phrase-${item.id}").performTouchInput { longClick() }
        compose.onNodeWithText("开启快速排序？").assertDoesNotExist()
        assertEquals(before, model.phrases.value); assertEquals("", model.state.value.editorText)
        assertEquals(false, QuickSortPreference(ApplicationProvider.getApplicationContext()).enabled.value)
    }
    @Test fun enabledSortingWorksForTilesAndGroupsWithoutEditingAndPersists() {
        val model = show()
        compose.runOnIdle { model.quickSort.choose(true) }
        val category = model.state.value.selectedCategory
        val before = model.phrases.value.filter { it.category == category }
        val from = center("quick-phrase-${before[0].id}", "phrases-vertical")
        val to = center("quick-phrase-${before[1].id}", "phrases-vertical")
        compose.onNodeWithTag("phrases-vertical").performTouchInput {
            down(from); advanceEventTime(700); moveTo(to, 120); advanceEventTime(100); up()
        }
        compose.waitForIdle()
        assertEquals(before[0].id, model.phrases.value.filter { it.category == category }[1].id)
        assertFalse(model.state.value.quickTextEditing); assertEquals("", model.state.value.editorText)
        val groups = model.categories.value
        val g1 = center("category-${groups[0]}", "category-tabs")
        val g2 = center("category-${groups[1]}", "category-tabs")
        compose.onNodeWithTag("category-tabs").performTouchInput { down(g1); advanceEventTime(700); moveTo(g2, 120); up() }
        compose.waitForIdle()
        assertEquals(groups[0], model.categories.value[1])
        val restored = testAppModel(ApplicationProvider.getApplicationContext())
        assertEquals(model.phrases.value.map { it.id }, restored.phrases.value.map { it.id })
        assertEquals(model.categories.value, restored.categories.value)
    }
    @Test fun holdingPolaroidButtonOpensCameraDirectly() {
        val model = show()
        compose.runOnIdle { model.closePanel() }
        compose.onNodeWithTag("dock-end").performTouchInput { longClick() }
        assertTrue(model.state.value.destination is Destination.CardCamera)
    }
    @Test fun homeHorizontalSwipeOpensTheRealPolaroidScene() {
        val model = show()
        compose.runOnIdle { model.closePanel() }
        compose.onNodeWithTag("page-layer/0/home").performTouchInput {
            swipe(Offset(width * .75f, height * .5f), Offset(width * .25f, height * .5f), 400)
        }
        compose.waitForIdle()
        assertEquals(SidePage.CARDS, model.state.value.sidePage)
        compose.onNodeWithTag("secondary-cards").assertIsDisplayed()
    }
}
