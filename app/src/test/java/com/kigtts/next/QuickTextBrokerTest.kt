@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class QuickTextBrokerTest {
    private lateinit var app:Application;private lateinit var model:AppViewModel;private lateinit var broker:QuickTextBroker
    @Before fun setup() {Dispatchers.setMain(UnconfinedTestDispatcher());app=ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("quick-phrases",0).edit().clear().commit();model=testAppModel(app)
        broker=model.quickTextPlugins.forPlugin("test.agent",setOf("quick-text-read","quick-text-write"))}
    @After fun end() {Dispatchers.resetMain()}
    private suspend fun snapshot()=(broker.request(QuickTextCommand.Query()) as QuickTextReply.Snapshot).value
    private suspend fun prepare(edits:List<QuickTextEdit>,id:String="transaction",revision:Long=model.quickTexts.revision.value)=
        broker.request(QuickTextCommand.Prepare(id,revision,edits))
    @Test fun backgroundHostAndLaterUiUseExactlyTheSameDataOwner()=runBlocking {
        val runtime=com.kigtts.next.manager.HostPluginRuntime.get(app)
        val headless=runtime.quickTextPlugins.forPlugin("test.background",setOf("quick-text-write"))
        val before=(headless.request(QuickTextCommand.Query()) as QuickTextReply.Snapshot).value
        val item=QuickEntry(groupId=before.groups.first().id,text="后台生成的条目")
        val plan=headless.request(QuickTextCommand.Prepare("background",before.revision,listOf(QuickTextEdit.PutEntry(item)))) as QuickTextReply.Prepared
        assertTrue(headless.request(QuickTextCommand.Commit(plan.value.token)) is QuickTextReply.Applied)
        val ui=AppViewModel(app);assertSame(runtime.quickTexts,ui.quickTexts);assertSame(runtime.mediaRepository,ui.mediaRepository)
        assertTrue(ui.phrases.value.any {it.id==item.id});runtime.detach(ui)
    }
    @Test fun stableGroupsAndRichMetadataSurviveOneAtomicCommitNativeEditingAndRestart()=runBlocking {
        val before=snapshot();val group=before.groups.first();val first=before.entries.first {it.groupId==group.id}
        val item=first.copy(text="Agent 创建",title="工具",richText=true,formats=listOf(QuickFormat(0,5,QuickFormatKind.UNDERLINE)),metadata=mapOf("test.agent/widget" to "calculator"))
        val prepared=prepare(listOf(QuickTextEdit.PutGroup(group.copy(name="新分组",icon="INFO")),QuickTextEdit.PutEntry(item))) as QuickTextReply.Prepared
        assertEquals(1,prepared.value.groupChanges)
        assertEquals(before.revision,model.quickTexts.revision.value)
        val applied=broker.request(QuickTextCommand.Commit(prepared.value.token)) as QuickTextReply.Applied
        assertEquals(before.revision+1,applied.revision);assertEquals(group.id,model.quickTexts.categoryIds.value["新分组"])
        val saved=model.phrases.value.first {it.id==first.id};model.content.beginPhrase(saved,saved.category);model.content.editPhrase {it.copy(title="人工修改")};model.content.savePhrase()
        val restarted=testAppModel(app);val restored=restarted.phrases.value.first {it.id==first.id}
        assertEquals("calculator",restored.metadata["test.agent/widget"]);assertEquals(group.id,restarted.quickTexts.categoryIds.value["新分组"])
        assertEquals(1,restored.formats.size)
    }
    @Test fun invalidSecondOperationAndStaleRevisionCannotPartiallyModifyTheLibrary()=runBlocking {
        val before=snapshot();val first=before.entries.first()
        val reply=prepare(listOf(QuickTextEdit.PutEntry(first.copy(text="不会保存")),QuickTextEdit.PutEntry(first.copy(groupId="missing"))))
        assertEquals(QuickTextError.INVALID,(reply as QuickTextReply.Failure).code);assertEquals(before,snapshot())
        val staged=prepare(listOf(QuickTextEdit.PutEntry(first.copy(text="过期修改"))),"stale") as QuickTextReply.Prepared
        model.quickTexts.add("用户修改",model.categories.value.first())
        assertEquals(QuickTextError.CONFLICT,(broker.request(QuickTextCommand.Commit(staged.value.token)) as QuickTextReply.Failure).code)
        assertFalse(model.phrases.value.any {it.text=="过期修改"})
    }
    @Test fun destructiveBatchesRequireNativeApprovalAndForeignTokensAreRejected()=runBlocking {
        val before=snapshot();val first=before.entries.first()
        val staged=prepare(listOf(QuickTextEdit.RemoveEntries(listOf(first.id)))) as QuickTextReply.Prepared
        assertTrue(staged.value.requiresConfirmation)
        assertEquals(QuickTextError.CONFIRMATION_REQUIRED,(broker.request(QuickTextCommand.Commit(staged.value.token)) as QuickTextReply.Failure).code)
        val foreign=model.quickTextPlugins.forPlugin("test.other",setOf("quick-text-write"))
        assertEquals(QuickTextError.PERMISSION_DENIED,(foreign.request(QuickTextCommand.Commit(staged.value.token)) as QuickTextReply.Failure).code)
        assertTrue(model.quickTextPlugins.approve(staged.value.token) is QuickTextReply.Applied)
        assertFalse(model.phrases.value.any {it.id==first.id})
    }
    @Test fun duplicatePreparationAndAppliedReceiptsAreIdempotentIncludingAfterRestart()=runBlocking {
        val first=snapshot().entries.first();val command=QuickTextCommand.Prepare("retry",model.quickTexts.revision.value,listOf(QuickTextEdit.PutEntry(first.copy(text="只提交一次"))))
        val staged=broker.request(command) as QuickTextReply.Prepared;assertEquals(staged,broker.request(command))
        val applied=broker.request(QuickTextCommand.Commit(staged.value.token));assertEquals(applied,broker.request(command))
        val restored=testAppModel(app).quickTextPlugins.forPlugin("test.agent",setOf("quick-text-write"))
        assertEquals(applied,restored.request(QuickTextCommand.Transaction("retry")))
        assertEquals(QuickTextError.CONFLICT,(broker.request(command.copy(edits=listOf(QuickTextEdit.PutEntry(first.copy(text="另一操作"))))) as QuickTextReply.Failure).code)
    }
    @Test fun permissionsActiveDraftAndDiscardAreEnforced()=runBlocking {
        val readOnly=model.quickTextPlugins.forPlugin("test.read",setOf("quick-text-read"));val first=snapshot().entries.first()
        assertTrue(readOnly.request(QuickTextCommand.Query()) is QuickTextReply.Snapshot)
        assertEquals(QuickTextError.PERMISSION_DENIED,(readOnly.request(QuickTextCommand.Prepare("no",model.quickTexts.revision.value,listOf(QuickTextEdit.RemoveEntries(listOf(first.id))))) as QuickTextReply.Failure).code)
        model.content.beginPhrase(model.phrases.value.first {it.id==first.id},model.phrases.value.first {it.id==first.id}.category)
        assertEquals(QuickTextError.BUSY,(prepare(listOf(QuickTextEdit.PutEntry(first.copy(text="覆盖草稿")))) as QuickTextReply.Failure).code)
        model.content.cancelPhrase()
        val pending=prepare(listOf(QuickTextEdit.PutEntry(first.copy(title="取消")))) as QuickTextReply.Prepared
        assertTrue(broker.request(QuickTextCommand.Discard(pending.value.token)) is QuickTextReply.Discarded)
        assertTrue(broker.request(QuickTextCommand.Transaction("transaction")) is QuickTextReply.Discarded)
        assertEquals(first,snapshot().entries.first {it.id==first.id})
    }
}
