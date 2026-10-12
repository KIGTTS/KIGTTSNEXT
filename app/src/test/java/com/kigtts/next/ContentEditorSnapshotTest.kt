package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import com.kigtts.next.media.MediaRepository
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ContentEditorSnapshotTest {
    @Test fun richFormattingSurvivesDraftRestorationAndCountsAsAnUnsavedChange() {
        val app=ApplicationProvider.getApplicationContext<Application>();val snapshot=ContentEditorSnapshot(app)
        val initial=listOf(com.kigtts.next.text.TextFormatRun(0,2,com.kigtts.next.text.TextFormat.BOLD))
        val draft=ContentDraft(UUID.randomUUID().toString(),false,"文本","你好","subtitle",formats=initial)
        assertFalse(draft.dirty)
        val edited=draft.copy(formats=initial+com.kigtts.next.text.TextFormatRun(0,2,com.kigtts.next.text.TextFormat.UNDERLINE))
        assertTrue(edited.dirty)
        assertEquals(edited,snapshot.read(snapshot.write(edited)))
    }
    @Test fun rapidDuplicateSaveAndBackDuringSaveCommitAndReturnExactlyOnce() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>();val repository=MediaRepository(app,PluginPaths(app))
        val entry=repository.html("项目","旧内容");var state=AppState();var returned=0
        val library=QuickTextLibrary(QuickPhraseStore(app))
        val content=ContentWorkspace(app,repository,library,{state},{state=it(state)},
            {state=state.copy(destination=it)},{returned++;state=state.copy(destination=Destination.Home)},{})
        content.editor(html=true,selectedMedia=entry.id)
        val first=async(start=CoroutineStart.UNDISPATCHED) { content.saveEditor("新内容","项目") }
        assertTrue(content.requestDiscard());assertFalse(content.discardRequested.value)
        val repeated=async(start=CoroutineStart.UNDISPATCHED) { content.saveEditor("重复保存","项目") }
        first.await();repeated.await()
        assertEquals(1,returned);assertNull(content.editorDraft.value)
        assertEquals("新内容",repository.file(repository.get(entry.id)!!)!!.readText())
    }
    @Test fun largeDraftAndCursorRestoreFromPrivateCacheWithoutEnteringTheBundle() {
        val app=ApplicationProvider.getApplicationContext<Application>();val snapshot=ContentEditorSnapshot(app)
        val draft=ContentDraft(UUID.randomUUID().toString(),true,"home.html","原始","phrase:test","media-id",
            "汉字".repeat(300000),"home.html","pages/home.html",321)
        val token=snapshot.write(draft);assertEquals(36,token.length)
        assertEquals(draft,ContentEditorSnapshot(app).read(token));assertNull(snapshot.read("../escape"))
    }
}
