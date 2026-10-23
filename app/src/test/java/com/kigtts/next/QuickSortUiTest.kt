package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
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
    @Test fun singleRowKeepsTheGrabbedFirstCardUnderTheFingerAfterSwapping() = verifyLeadingCardDrag(true)
    @Test fun singleColumnKeepsTheGrabbedFirstCardUnderTheFingerAfterSwapping() = verifyLeadingCardDrag(false)
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
