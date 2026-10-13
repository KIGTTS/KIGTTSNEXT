@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.state.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class EditorSaveOwnershipTest {
    private class Fixture(io:CoroutineDispatcher) {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val repository=MediaRepository(app,PluginPaths(app));val library:QuickTextLibrary
        var state=AppState();var backs=0;val content:ContentWorkspace
        init {
            app.getSharedPreferences("quick-phrases",0).edit().clear().commit()
            library=QuickTextLibrary(QuickPhraseStore(app))
            content=ContentWorkspace(app,repository,library,{state},{state=it(state)},{destination->
                state=state.copy(destination=destination,navigationStack=state.navigationStack+state.destination)
            },{
                backs++;val previous=state.navigationStack.lastOrNull() ?: Destination.Home
                state=state.copy(destination=previous,navigationStack=state.navigationStack.dropLast(1))
            },{},editorIo=io)
        }
        fun cover() {state=state.copy(navigationStack=state.navigationStack+state.destination,destination=Destination.Settings);content.onDestinationChanged(state.destination)}
        fun uncover() {state=state.copy(destination=state.navigationStack.last(),navigationStack=state.navigationStack.dropLast(1));content.onDestinationChanged(state.destination)}
    }
    @Test fun realHtmlWriteCompletesUnderAChildAndItsConfirmationCanOnlyCloseTheMatchingEditorOnce()=runTest {
        val f=Fixture(StandardTestDispatcher(testScheduler));val media=f.repository.html("页面","old")
        f.content.editorFile(media.id,"index.html","display");val token=f.content.editorDraft.value!!.token
        f.content.editEditor("new","index.html");assertTrue(f.content.requestDiscard())
        var cleanup:(()->Unit)?=null
        val save=launch(start=CoroutineStart.UNDISPATCHED) {f.content.saveEditor("new","index.html") {cleanup=it}}
        assertTrue(f.content.editorSavePending.value);assertFalse(f.content.editorSaveCompleted.value)
        f.cover();testScheduler.advanceUntilIdle();save.join()
        assertEquals("new",f.repository.htmlFiles.read(media.id,"index.html"))
        assertEquals(Destination.Settings,f.state.destination);assertEquals(0,f.backs)
        assertTrue(f.content.editorSavePending.value);assertTrue(f.content.editorSaveCompleted.value)
        requireNotNull(cleanup).invoke();assertEquals(Destination.Settings,f.state.destination);assertEquals(0,f.backs)
        assertFalse(f.content.cleanupEditorSave("different-editor"))
        f.uncover();assertEquals(Destination.ContentEditor(token),f.state.destination)
        assertTrue(f.content.cleanupEditorSave(token));assertFalse(f.content.cleanupEditorSave(token))
        requireNotNull(cleanup).invoke();assertEquals(1,f.backs);assertNull(f.content.editorDraft.value)
        assertFalse(f.content.editorSavePending.value);assertFalse(f.content.discardRequested.value)
    }
    @Test fun toolbarSaveReturnsOnForegroundAndALaterDoubleSaveCannotWriteAgain()=runTest {
        val f=Fixture(StandardTestDispatcher(testScheduler));val media=f.repository.html("页面","old")
        f.content.editorFile(media.id,"index.html","display")
        val save=launch(start=CoroutineStart.UNDISPATCHED) {f.content.saveEditor("new","index.html")}
        f.cover();testScheduler.advanceUntilIdle();save.join()
        assertEquals(Destination.Settings,f.state.destination);assertEquals(0,f.backs)
        f.content.saveEditor("wrong second write","index.html")
        assertEquals("new",f.repository.htmlFiles.read(media.id,"index.html"))
        f.uncover();assertEquals(Destination.Home,f.state.destination);assertEquals(1,f.backs)
        f.content.onDestinationChanged(Destination.Home);assertEquals(1,f.backs)
    }
    @Test fun anActualOwnerPopDropsOnlyCompletionAndFailedWritesRetainTheOriginalDraftForRetry()=runTest {
        val f=Fixture(StandardTestDispatcher(testScheduler));val media=f.repository.html("页面","old")
        f.content.editorFile(media.id,"index.html","display");f.content.editEditor("new","index.html")
        val token=f.content.editorDraft.value!!.token;assertTrue(f.content.requestDiscard())
        val file=f.repository.htmlFiles.file(media.id,"index.html");assertTrue(file.delete())
        val failure=async(start=CoroutineStart.UNDISPATCHED) {runCatching {f.content.saveEditor("new","index.html")}.exceptionOrNull()}
        testScheduler.advanceUntilIdle();assertNotNull(failure.await())
        assertEquals(token,f.content.editorDraft.value!!.token);assertFalse(f.content.editorSavePending.value)
        assertTrue(f.content.discardRequested.value);assertEquals(0,f.backs)
        file.writeText("old",Charsets.UTF_8)
        val retry=launch(start=CoroutineStart.UNDISPATCHED) {f.content.saveEditor("new","index.html")}
        testScheduler.advanceUntilIdle();retry.join();assertTrue(f.content.editorSaveCompleted.value)
        f.state=f.state.copy(destination=Destination.Home,navigationStack=emptyList());f.content.onDestinationChanged(Destination.Home)
        assertFalse(f.content.editorSavePending.value);assertNull(f.content.editorDraft.value)
        assertFalse(f.content.cleanupEditorSave(token));assertEquals(0,f.backs)
        assertEquals("new",f.repository.htmlFiles.read(media.id,"index.html"))
    }
    @Test fun aNewPhraseIsPersistedOnceAndItsCoveredSuccessfulFormSurvivesUntilOwnerCleanup()=runTest {
        val f=Fixture(StandardTestDispatcher(testScheduler));val before=f.library.revision.value
        f.content.beginPhrase(null,f.library.categories.value.first());f.content.editPhrase {it.copy(text="新条目")}
        val token=f.content.phraseDraft.value!!.key
        f.state=f.state.copy(destination=Destination.PhraseEditor(token,true),navigationStack=listOf(Destination.Home))
        assertTrue(f.content.savePhraseForExit(animated=true));val savedId=requireNotNull(f.content.phraseDraft.value!!.phraseId)
        assertEquals(before+1,f.library.revision.value)
        f.cover();assertFalse(f.content.cleanupPhraseSave(token));assertEquals(0,f.backs)
        assertFalse(f.content.savePhraseForExit(animated=true));assertEquals(before+1,f.library.revision.value)
        assertEquals(1,f.library.phrases.value.count {it.id==savedId})
        f.uncover();assertTrue(f.content.cleanupPhraseSave(token));assertFalse(f.content.cleanupPhraseSave(token))
        assertEquals(1,f.backs);assertNull(f.content.phraseDraft.value);assertFalse(f.content.phraseSavePending.value)
        assertEquals(before+1,f.library.revision.value)
    }
}
