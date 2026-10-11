package com.kigtts.next

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PhraseEditorNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "quick-phrases", "home-panel", "interaction").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { model ->
            compose.setContent { KigttsApp(model) }
            compose.runOnIdle { model.openPanel(HomePanel.QUICK_TEXT); model.editQuickTexts() }
        }
    }
    private fun step(time: Long) { compose.mainClock.advanceTimeBy(time); compose.waitForIdle() }
    private fun layer(model: AppViewModel) = compose.onNodeWithTag("page-layer/1/phrase-editor/" + model.content.phraseDraft.value!!.key)
    @Test fun detailsUseTheNormalPageEntranceAndReturnWithoutChangingTheSourceCard() {
        val model = show(); val id = model.phrases.value.first().id
        val source = compose.onNodeWithTag("edit-phrase-" + id); val before = source.getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        source.performClick(); step(80)
        assertTrue(model.state.value.destination is Destination.PhraseEditor)
        compose.onNode(isDialog()).assertDoesNotExist()
        val page = layer(model); val entering = page.getUnclippedBoundsInRoot()
        assertTrue(entering.left.value > 0f)
        compose.onRoot().captureRoboImage("build/previews/phrase-page-enter-mid.png")
        step(600); assertEquals(0f, page.getUnclippedBoundsInRoot().left.value, .1f)
        compose.onNodeWithTag("large-page-bar").assertIsDisplayed()
        compose.onNodeWithTag("cancel-phrase-details").performClick(); step(64)
        assertTrue(page.getUnclippedBoundsInRoot().left.value > 0f)
        step(1000); compose.mainClock.autoAdvance = true
        assertEquals(Destination.Home, model.state.value.destination); assertNull(model.content.phraseDraft.value)
        assertTrue(model.state.value.quickTextEditing); assertEquals(before, source.getUnclippedBoundsInRoot())
    }
    @Test fun predictiveBackHasFixedDirectionAndCancellationKeepsTheDraft() {
        val model = show()
        compose.onNodeWithTag("edit-phrase-" + model.phrases.value.first().id).performClick()
        compose.onNodeWithTag("phrase-detail-text").performTextReplacement("保留草稿")
        val dispatcher = compose.activity.onBackPressedDispatcher; val page = layer(model)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_RIGHT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 100f, .65f, BackEventCompat.EDGE_RIGHT))
        }
        step(64); assertTrue(page.getUnclippedBoundsInRoot().left.value > 0f)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }; step(800)
        assertEquals(0f, page.getUnclippedBoundsInRoot().left.value, .1f)
        assertEquals("保留草稿", model.content.phraseDraft.value!!.text)
        compose.runOnIdle { dispatcher.onBackPressed() }; step(1000)
        assertNull(model.content.phraseDraft.value); assertTrue(model.state.value.quickTextEditing)
        compose.mainClock.autoAdvance = true
    }
    @Test fun cancelDiscardsAndSaveCommitsThenReturnsToTheOrderingPanel() {
        val model = show(); val item = model.phrases.value.first(); val card = compose.onNodeWithTag("edit-phrase-" + item.id)
        card.performClick(); compose.onNodeWithTag("phrase-detail-text").performTextReplacement("取消的文字")
        compose.onNodeWithTag("cancel-phrase-details").performClick(); compose.waitForIdle()
        assertEquals(item.text, model.phrases.value.first { it.id == item.id }.text)
        card.performClick(); compose.onNodeWithTag("phrase-detail-text").performTextReplacement("保存的文字")
        compose.onNodeWithTag("save-phrase-details").performClick(); compose.waitForIdle()
        assertNull(model.content.phraseDraft.value); assertEquals(Destination.Home, model.state.value.destination)
        assertEquals("保存的文字", model.phrases.value.first { it.id == item.id }.text)
    }
    @Test fun htmlEditorIsAChildPageAndReturningPreservesTheSharedFields() {
        val model = show(); compose.onNodeWithTag("add-phrase").performClick()
        compose.onNodeWithTag("phrase-detail-text").performTextInput("共有字幕")
        val key = model.content.phraseDraft.value!!.key
        compose.onNodeWithTag("phrase-type-PAGE").performClick()
        compose.onNodeWithTag("phrase-edit-html").performScrollTo().performClick()
        assertTrue(model.state.value.destination is Destination.ContentEditor)
        assertTrue(model.state.value.navigationStack.last() is Destination.PhraseEditor)
        compose.runOnIdle { model.navigateBack() }; compose.waitForIdle()
        assertEquals(key, (model.state.value.destination as Destination.PhraseEditor).token)
        compose.onNodeWithTag("phrase-detail-text").assertTextContains("共有字幕")
    }
}
