package com.kigtts.next

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
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
class PhraseSaveBackUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun model(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("appearance", "quick-phrases", "home-panel", "interaction").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        return testAppModel(app).also { it.editPhraseDetails(it.phrases.value.first(), it.phrases.value.first().category) }
    }

    @Test fun backCanKeepEditingOrExplicitlyDiscardWithoutMutatingTheStoredPhrase() {
        val model = model()
        val original = model.phrases.value.first()
        compose.setContent { KigttsApp(model) }
        compose.onNodeWithTag("phrase-detail-text").performTextReplacement("尚未保存的正文")
        compose.onNodeWithTag("cancel-phrase-details").performClick()
        compose.onNodeWithTag("save-changes-save").assertIsEnabled()
        compose.onNodeWithTag("save-changes-discard").assertIsDisplayed()
        compose.onNodeWithTag("save-changes-keep").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals("尚未保存的正文", model.content.phraseDraft.value!!.text)
        assertEquals(original, model.phrases.value.first { it.id == original.id })
        compose.onNodeWithTag("cancel-phrase-details").performClick()
        compose.onNodeWithTag("save-changes-discard").performClick()
        compose.waitUntil(8_000) { model.state.value.destination == Destination.Home }
        assertEquals(Destination.Home, model.state.value.destination)
        assertNull(model.content.phraseDraft.value)
        assertEquals(original, model.phrases.value.first { it.id == original.id })
    }

    @Test fun savedInitialBaselineSurvivesAChildPageAndRecreationAndPredictiveSaveCommitsOnce() {
        val model = model()
        val original = model.phrases.value.first()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KigttsApp(model) }
        compose.onNodeWithTag("phrase-detail-text").performTextReplacement("返回时保存正文")
        val token = requireNotNull(model.content.phraseDraft.value).key
        compose.runOnIdle { model.navigate(Destination.Settings) }
        compose.waitForIdle()
        compose.runOnIdle { model.navigateBack() }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("phrase-detail-text").assertTextContains("返回时保存正文")
        assertEquals(token, (model.state.value.destination as Destination.PhraseEditor).token)
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 100f, .6f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackCancelled()
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals("返回时保存正文", model.content.phraseDraft.value!!.text)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithTag("save-changes-save").assertIsDisplayed().performClick()
        compose.waitUntil(8_000) { model.state.value.destination == Destination.Home }
        assertNull(model.content.phraseDraft.value)
        assertTrue(model.state.value.navigationStack.isEmpty())
        assertEquals("返回时保存正文", model.phrases.value.first { it.id == original.id }.text)
        val restored = QuickPhraseStore(ApplicationProvider.getApplicationContext<Application>()).load()
        assertEquals("返回时保存正文", restored.first { it.id == original.id }.text)
    }
}
