package com.kigtts.next

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
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
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class PhraseDialogTest {
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
    private fun capture(name: String) = compose.onNode(isDialog()).captureRoboImage("build/previews/" + name + ".png")

    @Test fun tappedCardTransformsThroughIntermediateBoundsIntoFullscreenAndBack() {
        val model = show()
        val card = compose.onNodeWithTag("edit-phrase-" + model.phrases.value.first().id)
        val original = card.getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        card.performClick(); repeat(2) { step(16) }; compose.onNode(isDialog()).assertExists()
        repeat(8) { step(16) }
        val container = compose.onNodeWithTag("phrase-dialog-container")
        val middle = container.getUnclippedBoundsInRoot()
        val window = compose.onNode(isDialog()).getUnclippedBoundsInRoot()
        assertTrue("Container grows from the card width", (middle.right - middle.left).value > (original.right - original.left).value + 2f)
        assertTrue("Container does not jump to full height", (middle.bottom - middle.top).value < (window.bottom - window.top).value - 20f)
        capture("phrase-container-enter-mid")
        step(600)
        assertEquals(window, container.getUnclippedBoundsInRoot())
        capture("phrase-fullscreen-details")
        compose.onNodeWithTag("cancel-phrase-details").performClick(); step(112)
        assertNotNull("Draft lives until the container has returned", model.content.phraseDraft.value)
        val returning = container.getUnclippedBoundsInRoot()
        assertTrue((returning.right - returning.left).value < (window.right - window.left).value - 2f)
        capture("phrase-container-exit-mid")
        step(600)
        compose.onNode(isDialog()).assertDoesNotExist()
        assertNull(model.content.phraseDraft.value)
        card.assertIsDisplayed()
        assertEquals(original, card.getUnclippedBoundsInRoot())
        compose.mainClock.autoAdvance = true
    }

    @Test fun predictiveCancelRestoresFullscreenAndCommitClosesOnlyTheDialog() {
        val model = show()
        compose.onNodeWithTag("edit-phrase-" + model.phrases.value.first().id).performClick()
        compose.onNodeWithTag("phrase-detail-text").assertIsDisplayed()
        val dispatcher = (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher
        val container = compose.onNodeWithTag("phrase-dialog-container")
        val full = container.getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 100f, .65f, BackEventCompat.EDGE_LEFT))
        }
        step(32)
        val prediction = container.getUnclippedBoundsInRoot()
        assertTrue((prediction.bottom - prediction.top).value < (full.bottom - full.top).value - 40f)
        capture("phrase-container-predictive-mid")
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }; step(800)
        assertEquals(full, container.getUnclippedBoundsInRoot())
        assertNotNull(model.content.phraseDraft.value)
        compose.runOnIdle { dispatcher.onBackPressed() }; step(800)
        assertNull(model.content.phraseDraft.value)
        assertTrue("Underlying quick-text editor stays open", model.state.value.quickTextEditing)
        compose.mainClock.autoAdvance = true
    }

    @Test fun cancellationDiscardsChangesAndSaveCommitsAfterReturn() {
        val model = show(); val item = model.phrases.value.first()
        val card = compose.onNodeWithTag("edit-phrase-" + item.id)
        card.performClick()
        compose.onNodeWithTag("phrase-detail-text").performTextReplacement("取消的文字")
        compose.onNodeWithTag("cancel-phrase-details").performClick()
        compose.waitUntil(5000) { model.content.phraseDraft.value == null }
        assertEquals(item.text, model.phrases.value.first { it.id == item.id }.text)
        card.performClick()
        compose.onNodeWithTag("phrase-detail-text").performTextReplacement("保存的文字")
        compose.onNodeWithTag("save-phrase-details").performClick()
        compose.waitUntil(5000) { model.content.phraseDraft.value == null }
        assertEquals("保存的文字", model.phrases.value.first { it.id == item.id }.text)
    }
}
