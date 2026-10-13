package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.plugins.control.AppControlHost
import com.kigtts.next.state.*
import com.kigtts.next.ui.KigttsApp
import com.kigtts.plugin.api.*
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowLooper

/** Formal Agent navigation covers the actual retained editor while its accepted dialog is leaving. */
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk=[35],qualifiers="zh-rCN-w393dp-h852dp-xxhdpi")
class EditorSaveCoveredUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun model():AppViewModel {
        val app=ApplicationProvider.getApplicationContext<Application>()
        listOf("quick-phrases","appearance","home-panel").forEach {app.getSharedPreferences(it,0).edit().clear().commit()}
        return testAppModel(app)
    }
    private fun agentCover(model:AppViewModel) {
        val agent=AppControlHost(model.pluginManager){model}.forPlugin("test.agent-navigation",setOf("app-control"))
        var reply:AppReply?=null
        compose.runOnIdle {compose.activity.lifecycleScope.launch {reply=agent.request(AppCommand.Navigate("settings"))}}
        compose.runOnIdle {ShadowLooper.idleMainLooper();Snapshot.sendApplyNotifications()}
        assertTrue(reply is AppReply.Data);assertEquals(Destination.Settings,model.state.value.destination)
        compose.mainClock.advanceTimeBy(600);compose.waitForIdle()
        assertEquals("An editor's accepted cleanup cannot pop the Agent's child page",Destination.Settings,model.state.value.destination)
    }
    @Test fun acceptedPhraseConfirmationKeepsTheSavedFormAcrossCoverageAndRecreationThenReturnsOnce() {
        val model=model();val original=model.phrases.value.first();val revision=model.quickTexts.revision.value
        model.editPhraseDetails(original,original.category)
        val token=model.content.phraseDraft.value!!.key
        val restoration=StateRestorationTester(compose);restoration.setContent {KigttsApp(model)}
        compose.onNodeWithTag("phrase-detail-text").performTextReplacement("保存后被Agent覆盖")
        compose.onNodeWithTag("cancel-phrase-details").performClick()
        compose.onNodeWithTag("save-changes-save").assertIsDisplayed()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("save-changes-save").performClick()
        assertTrue(model.content.phraseSaveCompleted.value);assertTrue(model.content.phraseSavePending.value)
        assertEquals(revision+1,model.quickTexts.revision.value)
        agentCover(model)
        assertEquals(token,model.content.phraseDraft.value!!.key)
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(Destination.Settings,model.state.value.destination)
        compose.runOnIdle {model.navigateBack();Snapshot.sendApplyNotifications()}
        compose.mainClock.autoAdvance=true
        compose.waitForIdle()
        awaitHome(model)
        compose.waitForIdle()
        assertNull(model.content.phraseDraft.value);assertFalse(model.content.phraseSavePending.value)
        assertTrue(model.state.value.navigationStack.isEmpty())
        assertEquals(revision+1,model.quickTexts.revision.value)
        assertEquals("保存后被Agent覆盖",model.phrases.value.single {it.id==original.id}.text)
        compose.onNode(isDialog()).assertDoesNotExist()
    }
    @Test fun acceptedTextConfirmationNeverPopsACoveringChildAndRestoredForegroundCompletesItsOwnExit() {
        val model=model();model.content.editor();val token=model.content.editorDraft.value!!.token
        val restoration=StateRestorationTester(compose);restoration.setContent {KigttsApp(model)}
        compose.onNodeWithTag("content-editor-text").performTextReplacement("正文成功保存")
        compose.onNodeWithTag("page-back").performClick()
        compose.onNodeWithTag("save-changes-save").assertIsDisplayed()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("save-changes-save").performClick()
        assertTrue(model.content.editorSaveCompleted.value);assertTrue(model.content.editorSavePending.value)
        agentCover(model)
        assertEquals(token,model.content.editorDraft.value!!.token);assertEquals("正文成功保存",model.state.value.editorText)
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {model.navigateBack();Snapshot.sendApplyNotifications()}
        compose.mainClock.autoAdvance=true
        compose.waitForIdle()
        awaitHome(model)
        compose.waitForIdle()
        assertNull(model.content.editorDraft.value);assertFalse(model.content.editorSavePending.value)
        assertFalse(model.content.discardRequested.value);assertTrue(model.state.value.navigationStack.isEmpty())
        assertEquals("正文成功保存",model.state.value.editorText)
        assertFalse(model.content.cleanupEditorSave(token))
        compose.onNode(isDialog()).assertDoesNotExist()
    }
    private fun awaitHome(model:AppViewModel) {
        assertEquals(Destination.Home,model.state.value.destination)
    }
}
