@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.plugins.stickers.StickerHost
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PolaroidAlbumRepositoryTest {
    private lateinit var app:Application;private lateinit var paths:PluginPaths;private lateinit var media:MediaRepository
    private lateinit var repo:StickerRepository;private lateinit var cards:CardRepository;private lateinit var host:StickerHost
    @Before fun start() {
        Dispatchers.setMain(UnconfinedTestDispatcher());app=ApplicationProvider.getApplicationContext();paths=PluginPaths(app)
        val root=File(app.getExternalFilesDir(null),"appdata/Kigtts/cards").canonicalFile
        check(root.toPath().startsWith(requireNotNull(app.getExternalFilesDir(null)).canonicalFile.toPath()));root.deleteRecursively()
        app.getSharedPreferences("contact-cards",0).edit().clear().commit()
        media=MediaRepository(app,paths);repo=StickerRepository(app,paths,media);cards=CardRepository(app,repo);host=StickerHost(repo,paths)
    }
    @After fun end() {Dispatchers.resetMain()}
    private fun image():StickerImage {
        val source=File(app.cacheDir,"album-photo.png");val bitmap=Bitmap.createBitmap(24,12,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN);source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        return repo.importResource(source)
    }
    private fun broker(write:Boolean=true)=host.forPlugin("test.albums",if(write)setOf("stickers-read","stickers-write") else setOf("stickers-read"))
    @Test fun albumsPersistWithIndependentPhotoOrderAndDeletionRetainsPhotosAndImageBytes() {
        val image=image();val bytes=repo.imageFile(image.id)!!.readBytes()
        val first=cards.create(PolaroidCard(title="默认",imageId=image.id))
        val travel=cards.createAlbum("旅行");val work=cards.createAlbum("工作")
        val second=cards.create(PolaroidCard(title="第二",imageId=image.id),travel.id)
        val third=cards.create(PolaroidCard(title="第三",imageId=image.id),travel.id)
        assertEquals(listOf(first),cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM))
        cards.renameAlbum(travel.id,"旅途");cards.orderCards(travel.id,listOf(third.id,second.id))
        assertEquals(listOf(third,second),cards.cardsInAlbum(travel.id))
        cards.moveCards(listOf(third.id),work.id);cards.moveCards(listOf(second.id),work.id)
        cards.moveCards(listOf(third.id),work.id)
        assertEquals(listOf(second,third),cards.cardsInAlbum(work.id));assertTrue(cards.cardsInAlbum(travel.id).isEmpty())
        cards.orderAlbums(listOf(work.id,CardRepository.DEFAULT_ALBUM,travel.id));cards.removeAlbum(work.id)
        assertEquals(listOf(first,second,third),cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM))
        assertEquals(listOf(CardRepository.DEFAULT_ALBUM,travel.id),cards.albums.value.map {it.id})
        assertEquals("旅途",cards.albums.value.last().name);assertEquals(3,repo.cards.value.size)
        val restored=StickerRepository(app,paths,media)
        assertEquals(repo.snapshot.value,restored.snapshot.value);assertArrayEquals(bytes,restored.imageFile(image.id)!!.readBytes())
    }
    @Test fun legacyCatalogWithoutAlbumsMigratesEveryPhotoOnceAndNextSavePersistsMembership() {
        val first=cards.create(PolaroidCard(title="旧一"));val second=cards.create(PolaroidCard(title="旧二"))
        repo.apply(listOf(StickerEdit.OrderCards(listOf(first.id,second.id))))
        val file=File(repo.root,"catalog.json");val json=JSONObject(file.readText());val value=JSONObject(json.getString("snapshot"))
        value.remove("albums");json.put("snapshot",value.toString());file.writeText(json.toString())
        val restored=StickerRepository(app,paths,media);val migrated=CardRepository(app,restored)
        assertEquals(listOf(first,second),migrated.cardsInAlbum(CardRepository.DEFAULT_ALBUM))
        assertEquals(1,migrated.albums.value.size);assertEquals(restored.snapshot.value,StickerRepository(app,paths,media).snapshot.value)
        migrated.renameAlbum(CardRepository.DEFAULT_ALBUM,"我的照片")
        val persisted=PluginJson.decodeFromString<StickerSnapshot>(JSONObject(file.readText()).getString("snapshot"))
        assertEquals(listOf(first.id,second.id),persisted.albums.single().cardIds);assertEquals("我的照片",persisted.albums.single().name)
    }
    @Test fun invalidCompoundMovesIncompleteSortingAndDefaultRemovalNeverPartiallyChangeCatalog() {
        val first=cards.create(PolaroidCard(title="一"));val second=cards.create(PolaroidCard(title="二"));val before=repo.snapshot.value
        val invalid=listOf(
            listOf(StickerEdit.PutAlbum("new","新册"),StickerEdit.MoveAlbumCards("new",listOf("missing"))),
            listOf(StickerEdit.MoveAlbumCards("missing",listOf(first.id))),
            listOf(StickerEdit.OrderAlbumCards(CardRepository.DEFAULT_ALBUM,listOf(first.id,first.id))),
            listOf(StickerEdit.OrderAlbumCards(CardRepository.DEFAULT_ALBUM,listOf(second.id))),
            listOf(StickerEdit.OrderAlbums(emptyList())),
            listOf(StickerEdit.RemoveAlbums(listOf(CardRepository.DEFAULT_ALBUM))),
            listOf(StickerEdit.PutAlbum("bad"," ")))
        invalid.forEach {assertTrue(runCatching {repo.apply(it)}.isFailure);assertEquals(before,repo.snapshot.value)}
        assertEquals(before,StickerRepository(app,paths,media).snapshot.value)
    }
    @Test fun newUpdatedRemovedAndGloballySortedCardsKeepExactlyOneValidMembership() {
        val first=cards.create(PolaroidCard(title="一"));val second=cards.create(PolaroidCard(title="二"));val third=cards.create(PolaroidCard(title="三"))
        val album=cards.createAlbum("另册");cards.moveCards(listOf(second.id,third.id),album.id)
        val updated=cards.update(second.copy(description="更新正文"))
        assertEquals(listOf(updated,third),cards.cardsInAlbum(album.id))
        repo.apply(listOf(StickerEdit.OrderCards(listOf(third.id,first.id,second.id))))
        assertEquals(listOf(third,updated),cards.cardsInAlbum(album.id))
        cards.remove(third.id);assertEquals(listOf(updated),cards.cardsInAlbum(album.id))
        val fourth=cards.create(PolaroidCard(title="四"));assertEquals(listOf(fourth,first),cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM))
        val assigned=repo.albums.value.flatMap {it.cardIds};assertEquals(repo.cards.value.map {it.id}.toSet(),assigned.toSet());assertEquals(assigned.size,assigned.distinct().size)
    }
    @Test fun albumLimitRejectsTheEntireExtraBatchAndPreservesDurableCatalog() {
        repo.apply((0 until 100).map {StickerEdit.PutAlbum("album.$it","相册 $it")})
        repo.apply((100 until 127).map {StickerEdit.PutAlbum("album.$it","相册 $it")})
        val before=repo.snapshot.value;assertEquals(128,before.albums.size)
        val error=runCatching {repo.apply(listOf(StickerEdit.PutAlbum("overflow","超出")))}.exceptionOrNull()
        assertEquals(StickerError.TOO_LARGE,(error as StickerFault).code)
        assertEquals(before,repo.snapshot.value);assertEquals(before,StickerRepository(app,paths,media).snapshot.value)
    }
    @Test fun readOnlyAlbumQueriesUseCatalogRevisionAndReturnCompleteOrderedCardModels()=runBlocking {
        val image=image();val album=cards.createAlbum("读取");val layer=CardLayer(kind=CardLayerKind.TEXT,text="😀",flipX=true)
        val first=cards.create(PolaroidCard(title="全字段",imageId=image.id,originalImageId=image.id,photoX=.2,photoZoom=2.0,
            frameStyleId="instax.wide.classic",layers=listOf(layer)),album.id)
        val second=cards.create(PolaroidCard(title="第二"),album.id);cards.orderCards(album.id,listOf(second.id,first.id))
        val read=broker(false);val revision=repo.snapshot.value.revision
        val metadata=read.request(StickerCommand.QueryAlbums(1,1,revision)) as StickerReply.Albums
        assertEquals(listOf(album.id),metadata.items.map {it.id});assertEquals(2,metadata.total)
        val beyond=read.request(StickerCommand.QueryAlbums(Int.MAX_VALUE,50,revision)) as StickerReply.Albums
        assertTrue(beyond.items.isEmpty());assertNull(beyond.nextOffset)
        val page=(read.request(StickerCommand.QueryAlbumCards(album.id,1,1,revision)) as StickerReply.Snapshot).value
        assertEquals(listOf(first),page.cards);assertEquals(2,page.total);assertNull(page.nextOffset);assertEquals(listOf(second.id,first.id),page.albums.single().cardIds)
        assertEquals(StickerError.CONFLICT,(read.request(StickerCommand.QueryAlbumCards(album.id,revision=revision-1)) as StickerReply.Failure).code)
        assertEquals(StickerError.NOT_FOUND,(read.request(StickerCommand.QueryAlbumCards("missing")) as StickerReply.Failure).code)
        assertEquals(StickerError.PERMISSION_DENIED,(read.request(StickerCommand.Prepare("write-denied",revision,listOf(StickerEdit.PutAlbum("new","拒绝")))) as StickerReply.Failure).code)
        val features=read.request(StickerCommand.Describe) as StickerReply.Features
        assertTrue(features.operations.containsAll(listOf("query-albums","query-album-cards","album.move-cards")))
        assertFalse(PluginJson.encodeToString<StickerReply>(StickerReply.Snapshot(page)).contains(repo.root.absolutePath))
    }
    @Test fun pluginAlbumTransactionAndConfirmedRemovalPersistWithoutLosingPhotosOrResources()=runBlocking {
        val image=image();val photo=cards.create(PolaroidCard(title="保留",imageId=image.id));val plugin=broker()
        val create=StickerCommand.Prepare("album-create",repo.snapshot.value.revision,listOf(StickerEdit.PutAlbum("remote","插件相册"),StickerEdit.MoveAlbumCards("remote",listOf(photo.id))))
        val prepared=plugin.request(create) as StickerReply.Prepared
        assertEquals(prepared,plugin.request(create));assertTrue(plugin.request(StickerCommand.Commit(prepared.value.token)) is StickerReply.Applied)
        val transaction=plugin.request(StickerCommand.Transaction("album-create"));assertTrue(transaction is StickerReply.Applied)
        val restored=StickerRepository(app,paths,media);assertEquals(listOf(photo.id),restored.albums.value.first {it.id=="remote"}.cardIds)
        val remove=plugin.request(StickerCommand.Prepare("album-remove",repo.snapshot.value.revision,listOf(StickerEdit.RemoveAlbums(listOf("remote"))))) as StickerReply.Prepared
        assertTrue(remove.value.requiresConfirmation);assertTrue(remove.value.titles.single().contains("照片将保留"))
        assertEquals(StickerError.CONFIRMATION_REQUIRED,(plugin.request(StickerCommand.Commit(remove.value.token)) as StickerReply.Failure).code)
        val result=host.approve(remove.value.token);assertTrue(result is StickerReply.Applied);assertEquals(result,plugin.request(StickerCommand.Commit(remove.value.token)))
        assertEquals(listOf(photo),repo.cards.value);assertTrue(repo.imageFile(image.id)!!.isFile)
        assertEquals(listOf(photo.id),repo.albums.value.single().cardIds)
        assertEquals(repo.snapshot.value,StickerRepository(app,paths,media).snapshot.value)
    }
}
