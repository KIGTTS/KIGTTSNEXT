@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.CardRepository
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.ui.cards.CardPhotoSelection
import com.kigtts.next.ui.cards.CardWorkspaceActions
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class CardWorkspaceActionsTest {
    private fun actions(scope:CoroutineScope,io:CoroutineDispatcher,messages:MutableList<String> = mutableListOf()):CardWorkspaceActions {
        val app=ApplicationProvider.getApplicationContext<Application>();val external=requireNotNull(app.getExternalFilesDir(null)).canonicalFile
        val root=File(external,"appdata/Kigtts/cards").canonicalFile
        check(root.toPath().startsWith(external.toPath())&&root!=external);root.deleteRecursively()
        val paths=PluginPaths(app);val stickers=StickerRepository(app,paths,MediaRepository(app,paths));val cards=CardRepository(app,stickers)
        return CardWorkspaceActions(app,cards,stickers,scope,{}, {messages+=it}, {_,_->},io)
    }
    @Test fun alreadyCancelledScopeCannotStartWorkOrLatchBusy()=runTest {
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler));scope.cancel()
        val actions=actions(scope,StandardTestDispatcher(testScheduler));var runs=0
        actions.work({runs++});testScheduler.advanceUntilIdle()
        assertEquals(0,runs);assertFalse(actions.busy)
    }
    @Test fun cancellationBeforeIoDispatchReleasesTheBusyLockAndSelectionEvenIfCleanupThrows()=runTest {
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler,name="actions"))
        val messages=mutableListOf<String>();val actions=actions(scope,StandardTestDispatcher(testScheduler,name="io"),messages)
        var releases=0
        actions.importPhoto(CardPhotoSelection(Uri.parse("content://test/unopened")) {releases++;error("cleanup fixture")})
        assertTrue(actions.busy);scope.cancel();testScheduler.advanceUntilIdle()
        assertFalse(actions.busy);assertEquals(1,releases);assertTrue(messages.isEmpty())
    }
    @Test fun duplicateWorkRunsOnceAndFailureDoesNotBlockTheNextOperation()=runTest {
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler,name="actions"))
        val messages=mutableListOf<String>();val actions=actions(scope,StandardTestDispatcher(testScheduler,name="io"),messages)
        var runs=0;val completed=mutableListOf<Int>()
        try {
            actions.work<Int>({runs++;error("失败 fixture")})
            actions.work({runs+=100;100}) {completed+=it}
            assertTrue(actions.busy);testScheduler.advanceUntilIdle()
            assertEquals(1,runs);assertFalse(actions.busy);assertTrue(completed.isEmpty());assertEquals(1,messages.size)
            actions.work({42}) {completed+=it};testScheduler.advanceUntilIdle()
            assertEquals(listOf(42),completed);assertFalse(actions.busy)
        } finally {scope.cancel()}
    }
    @Test fun inactiveScopeRejectsImportAndReleasesItsTemporaryResourceOnce()=runTest {
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler));scope.cancel()
        val actions=actions(scope,StandardTestDispatcher(testScheduler));var releases=0
        actions.importPhoto(CardPhotoSelection(Uri.parse("content://test/unopened")) {releases++})
        testScheduler.advanceUntilIdle();assertFalse(actions.busy);assertEquals(1,releases)
    }
}
