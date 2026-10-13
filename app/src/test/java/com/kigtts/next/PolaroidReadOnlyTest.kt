@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.plugins.stickers.StickerHost
import com.kigtts.next.state.CardWorkspace
import com.kigtts.next.state.Destination
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PolaroidReadOnlyTest {
    @Before fun main() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }
    private class Fixture(limit:Int=8*1024*1024) {
        val app=ApplicationProvider.getApplicationContext<Application>();val paths=PluginPaths(app)
        val media=MediaRepository(app,paths);val repo:StickerRepository;val cards:CardRepository
        var uiCalls=0
        init {
            val external=requireNotNull(app.getExternalFilesDir(null)).canonicalFile
            val root=File(external,"appdata/Kigtts/cards").canonicalFile
            check(root.toPath().startsWith(external.toPath())&&root!=external);root.deleteRecursively()
            repo=StickerRepository(app,paths,media,catalogByteLimit=limit);cards=CardRepository(app,repo)
        }
        val host by lazy { StickerHost(repo,paths,{_,_->uiCalls++;true}) }
        val broker by lazy { host.forPlugin("test.receive",setOf("stickers-read","stickers-write")) }
        suspend fun prepare(id:String,edits:List<StickerEdit>)=broker.request(StickerCommand.Prepare(id,repo.snapshot.value.revision,edits))
    }
    @Test fun nativeReceiveAndCopyPreserveReadOnlyAfterRestartAndEveryDesignWriteRejectsAtomically() {
        val f=Fixture();val card=f.cards.receive(PolaroidCard(title="",description="接收的照片"))
        val before=f.repo.snapshot.value
        val failure=runCatching {f.cards.update(card.copy(description="改写"))}.exceptionOrNull() as StickerFault
        assertEquals(StickerError.PERMISSION_DENIED,failure.code);assertEquals(before,f.repo.snapshot.value)
        val copy=f.cards.copy(card);assertTrue(f.cards.isReadOnly(copy.id));assertEquals(card.description,copy.description)
        val restored=StickerRepository(f.app,f.paths,f.media);assertTrue(restored.isCardReadOnly(card.id));assertTrue(restored.isCardReadOnly(copy.id))
        assertEquals(setOf(card.id,copy.id),restored.readOnlyCards.value)
        val candidate=restored.snapshot.value.copy(cards=restored.cards.value.map {if(it.id==card.id)it.copy(title="绕过plan") else it})
        val blocked=runCatching {restored.replace(candidate,restored.snapshot.value.revision)}.exceptionOrNull() as StickerFault
        assertEquals(StickerError.PERMISSION_DENIED,blocked.code);assertEquals("",restored.cards.value.single {it.id==card.id}.title)
        assertTrue(runCatching {restored.exportCard(card.copy(photoZoom=2.0))}.isFailure)
    }
    @Test fun pluginCannotModifyOrOpenAnEditorAndAProtectedFrameCannotBeReplaced()=runBlocking {
        val f=Fixture();val frame=PolaroidFrameStyle("test.receive.frame","来源边框",color=0xFFFFFFFF,owner="test.receive")
        f.repo.apply(listOf(StickerEdit.PutFrameStyle(frame)))
        val card=f.cards.receive(PolaroidCard(title="接收",frameStyleId=frame.id));val before=f.repo.snapshot.value
        val edit=f.prepare("blocked-card",listOf(StickerEdit.PutCard(card.copy(title="伪造")))) as StickerReply.Failure
        assertEquals(StickerError.PERMISSION_DENIED,edit.code);assertEquals(before,f.repo.snapshot.value)
        val change=f.prepare("blocked-frame",listOf(StickerEdit.PutFrameStyle(frame.copy(color=0xFF000000)))) as StickerReply.Failure
        assertEquals(StickerError.PERMISSION_DENIED,change.code)
        val open=f.broker.request(StickerCommand.OpenUi(StickerUiTarget.EDIT_CARD,card.id)) as StickerReply.Failure
        assertEquals(StickerError.PERMISSION_DENIED,open.code);assertEquals(0,f.uiCalls)
        assertTrue(f.broker.request(StickerCommand.OpenUi(StickerUiTarget.CARDS,card.id)) is StickerReply.UiOpened)
        assertEquals(1,f.uiCalls)
        val actual=(f.broker.request(StickerCommand.Query(StickerScope.CARDS)) as StickerReply.Snapshot).value.cards.single()
        assertEquals(card,actual)
    }
    @Test fun receivedPhotosStillExportAndMoveOrDeleteAsCollectionItemsButDeletionCannotUnlockTheSameId()=runBlocking {
        val f=Fixture();val card=f.cards.receive(PolaroidCard(title=""));val album=f.cards.createAlbum("收藏")
        val exported=f.repo.exportCard(card.id)
        val bitmap=exported.inputStream().use {requireNotNull(BitmapFactory.decodeStream(it))};assertTrue(bitmap.width>0);bitmap.recycle()
        assertTrue(f.broker.request(StickerCommand.ExportCard(card.id,"exports/received-${card.id}.png")) is StickerReply.Resource)
        val move=f.prepare("move",listOf(StickerEdit.MoveAlbumCards(album.id,listOf(card.id)))) as StickerReply.Prepared
        assertTrue(f.broker.request(StickerCommand.Commit(move.value.token)) is StickerReply.Applied)
        assertEquals(listOf(card),f.cards.cardsInAlbum(album.id));assertTrue(f.cards.isReadOnly(card.id))
        val remove=f.prepare("remove",listOf(StickerEdit.RemoveCards(listOf(card.id)))) as StickerReply.Prepared
        assertEquals(StickerError.CONFIRMATION_REQUIRED,(f.broker.request(StickerCommand.Commit(remove.value.token)) as StickerReply.Failure).code)
        assertTrue(f.host.approve(remove.value.token) is StickerReply.Applied);assertTrue(f.cards.cards.value.isEmpty())
        val restored=StickerRepository(f.app,f.paths,f.media)
        val resurrect=runCatching {restored.apply(listOf(StickerEdit.PutCard(card.copy(title="已解锁？"))))}.exceptionOrNull() as StickerFault
        assertEquals(StickerError.PERMISSION_DENIED,resurrect.code);assertTrue(restored.cards.value.isEmpty())
    }
    @Test fun failedReceiveDoesNotPublishPermissionsAndOldEditableCatalogsStayEditable() {
        val f=Fixture(2048);val card=f.cards.create(PolaroidCard(title="自有"));val before=f.repo.snapshot.value
        val received=PolaroidCard(title="接收",description="字".repeat(8192))
        assertTrue(runCatching {f.cards.receive(received)}.isFailure);assertEquals(before,f.repo.snapshot.value)
        assertFalse(f.cards.isReadOnly(received.id));assertTrue(f.repo.readOnlyCards.value.isEmpty())
        val restored=StickerRepository(f.app,f.paths,f.media)
        val editable=CardRepository(f.app,restored);assertFalse(editable.isReadOnly(card.id))
        editable.update(card.copy(title="仍可编辑"));assertEquals("仍可编辑",editable.cards.value.single().title)
        restored.markCardReadOnly(card.id);val revision=restored.snapshot.value.revision
        restored.markCardReadOnly(card.id);assertEquals(revision,restored.snapshot.value.revision)
        assertTrue(StickerRepository(f.app,f.paths,f.media).isCardReadOnly(card.id))
    }
    @Test fun nativeWorkspaceEditorGateDoesNotChangePreviewOrPopTheCallingScene() {
        val f=Fixture();val card=f.cards.receive(PolaroidCard(title=""));val destinations=mutableListOf<Destination>();var shows=0
        val workspace=CardWorkspace({destinations+=it},{shows++},{f.cards.albums.value},
            {id->f.cards.cards.value.any {it.id==id}},{id->!f.cards.isReadOnly(id)})
        assertFalse(workspace.open(StickerUiTarget.EDIT_CARD,card.id));assertTrue(destinations.isEmpty())
        assertTrue(workspace.open(StickerUiTarget.CARDS,card.id));assertEquals(1,shows)
        assertEquals(card.id,workspace.previewRequest.value.cardId)
        val own=f.cards.create(PolaroidCard(title=""));assertTrue(workspace.open(StickerUiTarget.EDIT_CARD,own.id))
        assertEquals(listOf(Destination.CardEditor(own.id)),destinations)
    }
}
