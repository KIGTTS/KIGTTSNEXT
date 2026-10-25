package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.state.*
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35])
class CardPrintCoordinatorTest {
    internal class Fixture {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val paths=PluginPaths(app)
        val stickers=StickerRepository(app,paths,MediaRepository(app,paths))
        val cards=CardRepository(app,stickers)
        val state=MutableStateFlow(AppState(destination=Destination.Cards))
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val printed=mutableListOf<String>();val previewed=mutableListOf<String>();val messages=mutableListOf<String>();var backs=0
        var selected=CardRepository.DEFAULT_ALBUM
        val coordinator=CardCaptureCoordinator(cards,stickers,scope,{state.value},
            {state.value=state.value.copy(destination=it)},
            {backs++;state.value=state.value.copy(destination=Destination.Cards)},
            {messages+=it},selectedAlbum={selected},onPrintedCard={printed+=it},onPreviewedCard={previewed+=it})
        fun photo():File {
            val file=File(app.cacheDir,"print-${UUID.randomUUID()}.jpg")
            val bitmap=Bitmap.createBitmap(46,62,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}
            try {file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,95,it))}} finally {bitmap.recycle()}
            return file
        }
        suspend fun waitForPrint(source:File):CardPrintRequest {
            withTimeout(5_000) {while(coordinator.printRequest.value==null||source.exists())delay(10)}
            return requireNotNull(coordinator.printRequest.value)
        }
        fun assertSaved(card:PolaroidCard) {
            assertTrue(cards.cards.value.any {it==card})
            assertTrue(requireNotNull(stickers.imageFile(requireNotNull(card.imageId))).isFile)
            assertTrue(requireNotNull(stickers.imageFile(requireNotNull(card.originalImageId))).isFile)
        }
    }
    @Test fun printWaitsInItsSceneThenReturnsAndOpensSavedCardExactlyOnce()=runBlocking {
        val f=Fixture();val source=f.photo();val album=f.cards.createAlbum("打印相册");f.selected=album.id
        val before=f.cards.cards.value.size
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.MINI,print=true)
            val request=f.waitForPrint(source)
            assertEquals("",request.card.title)
            assertEquals(scene.requestId,request.requestId);assertEquals(scene,f.state.value.destination);assertEquals(0,f.backs)
            assertEquals(before+1,f.cards.cards.value.size);assertEquals(request.card.id,f.cards.cardsInAlbum(album.id).single().id);f.assertSaved(request.card)
            val duplicate=f.photo();f.coordinator.captured(scene,duplicate,duplicate,PolaroidFormat.MINI,print=true)
            assertFalse(duplicate.exists());assertEquals(before+1,f.cards.cards.value.size)
            assertFalse(f.coordinator.finishPrint("different-request"))
            assertEquals(request,f.coordinator.printRequest.value);assertEquals(0,f.backs)
            assertTrue(f.coordinator.finishPrint(request.requestId));assertFalse(f.coordinator.finishPrint(request.requestId))
            assertNull(f.coordinator.printRequest.value);assertEquals(Destination.Cards,f.state.value.destination)
            assertEquals(1,f.backs);assertEquals(listOf(request.card.id),f.printed);f.assertSaved(request.card)
        } finally {f.scope.cancel();source.delete()}
    }
    @Test fun leavingBeforePrintFinishesDropsOnlyTheRequestAndCannotNavigateAnotherScene()=runBlocking {
        val f=Fixture();val source=f.photo()
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.SQUARE,print=true)
            val request=f.waitForPrint(source)
            val other=Destination.CardCamera("new-scene")
            f.state.value=f.state.value.copy(destination=other);f.coordinator.onDestinationChanged(other)
            assertNull(f.coordinator.printRequest.value);assertFalse(f.coordinator.finishPrint(request.requestId))
            assertEquals(other,f.state.value.destination);assertEquals(0,f.backs);assertTrue(f.printed.isEmpty());f.assertSaved(request.card)
        } finally {f.scope.cancel();source.delete()}
    }
    @Test fun finishAlsoGuardsAgainstAStaleSceneIfNavigationNotificationHasNotRun()=runBlocking {
        val f=Fixture();val source=f.photo()
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.WIDE,print=true)
            val request=f.waitForPrint(source)
            f.state.value=f.state.value.copy(destination=Destination.Home)
            assertFalse(f.coordinator.finishPrint(request.requestId))
            assertNull(f.coordinator.printRequest.value);assertEquals(Destination.Home,f.state.value.destination)
            assertEquals(0,f.backs);assertTrue(f.printed.isEmpty());f.assertSaved(request.card)
        } finally {f.scope.cancel();source.delete()}
    }
    @Test fun childPageCoverageRetainsPrintUntilTheCameraReturnsAndFinishesExactlyOnce()=runBlocking {
        val f=Fixture();val source=f.photo()
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.SQUARE,print=true)
            val request=f.waitForPrint(source)
            f.state.value=f.state.value.copy(destination=Destination.Settings,navigationStack=listOf(Destination.Cards,scene))
            f.coordinator.onDestinationChanged(Destination.Settings)
            assertEquals(request,f.coordinator.printRequest.value)
            assertFalse(f.coordinator.finishPrint(request.requestId))
            assertEquals(request,f.coordinator.printRequest.value);assertEquals(Destination.Settings,f.state.value.destination)
            assertEquals(0,f.backs);assertTrue(f.printed.isEmpty());f.assertSaved(request.card)
            f.coordinator.open()
            assertEquals(Destination.Settings,f.state.value.destination);assertEquals(request,f.coordinator.printRequest.value)
            assertEquals(listOf("照片正在保存，请稍候"),f.messages)
            f.state.value=f.state.value.copy(destination=scene,navigationStack=listOf(Destination.Cards))
            f.coordinator.onDestinationChanged(scene)
            assertTrue(f.coordinator.finishPrint(request.requestId));assertFalse(f.coordinator.finishPrint(request.requestId))
            assertNull(f.coordinator.printRequest.value);assertEquals(1,f.backs);assertEquals(listOf(request.card.id),f.printed)
            f.assertSaved(request.card)
        } finally {f.scope.cancel();source.delete()}
    }
    @Test fun retakingAnEditorPhotoNeverPrintsOrMutatesItsSavedDraft()=runBlocking {
        val f=Fixture();val saved=f.cards.create(PolaroidCard(title="仍是原标题"));val source=f.photo()
        try {
            f.coordinator.open(saved.id);val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,source,source,PolaroidFormat.SQUARE,print=true)
            withTimeout(5_000) {while(f.coordinator.result.value==null||source.exists())delay(10)}
            assertNull(f.coordinator.printRequest.value);assertEquals(saved,f.cards.cards.value.first {it.id==saved.id})
            assertEquals(saved.id,f.coordinator.result.value?.cardId);assertEquals(1,f.backs);assertTrue(f.printed.isEmpty())
        } finally {f.scope.cancel();source.delete()}
    }
    @Test fun failedCaptureCleansItsFilesAndCanAcceptTheNextPrint()=runBlocking {
        val f=Fixture();val invalid=File(f.app.cacheDir,"invalid-${UUID.randomUUID()}.jpg").apply {writeText("not a photo")}
        val valid=f.photo()
        try {
            f.coordinator.open();val scene=f.state.value.destination as Destination.CardCamera
            f.coordinator.captured(scene,invalid,invalid,PolaroidFormat.SQUARE,print=true)
            withTimeout(5_000) {while(invalid.exists()||f.messages.isEmpty())delay(10)}
            assertNull(f.coordinator.printRequest.value);assertEquals(1,f.backs);assertTrue(f.printed.isEmpty())
            f.coordinator.open();f.coordinator.captured(f.state.value.destination as Destination.CardCamera,valid,valid,PolaroidFormat.SQUARE,print=true)
            f.assertSaved(f.waitForPrint(valid).card)
        } finally {f.scope.cancel();invalid.delete();valid.delete()}
    }
}
