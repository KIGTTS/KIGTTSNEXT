package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class PolaroidPhotoOrderTest {
    private fun repository():StickerRepository {
        val app=ApplicationProvider.getApplicationContext<Application>();val external=requireNotNull(app.getExternalFilesDir(null)).canonicalFile
        val root=File(external,"appdata/Kigtts/cards").canonicalFile
        check(root.toPath().startsWith(external.toPath())&&root!=external);root.deleteRecursively()
        val paths=PluginPaths(app);return StickerRepository(app,paths,MediaRepository(app,paths))
    }
    @Test fun newNativeAndPluginCardsPrependWhileUpdatesAndManualOrderKeepTheirPlacesAndDates() {
        val repo=repository();val cards=CardRepository(repo.context,repo);val start=System.currentTimeMillis()
        val a=cards.create(PolaroidCard(title="一"));val b=cards.create(PolaroidCard(title="二"))
        assertEquals(listOf(b.id,a.id),cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM).map {it.id})
        val created=cards.createdAt(a.id);assertTrue(created in start..System.currentTimeMillis())
        cards.orderCards(CardRepository.DEFAULT_ALBUM,listOf(a.id,b.id))
        val c=PolaroidCard(title="插件新照片");repo.apply(listOf(StickerEdit.PutCard(c)))
        assertEquals(listOf(c.id,a.id,b.id),cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM).map {it.id})
        cards.update(a.copy(description="只更新正文"))
        assertEquals(listOf(c.id,a.id,b.id),cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM).map {it.id});assertEquals(created,cards.createdAt(a.id))
        val restored=StickerRepository(repo.context,repo.paths,repo.media);val restoredCards=CardRepository(repo.context,restored)
        assertEquals(listOf(c.id,a.id,b.id),restoredCards.cardsInAlbum(CardRepository.DEFAULT_ALBUM).map {it.id})
        assertEquals(created,restoredCards.createdAt(a.id))
    }
    @Test fun movesPrependTheBatchPreserveItsOrderAndARepeatedSameAlbumMoveDoesNotReorder() {
        val repo=repository();val cards=CardRepository(repo.context,repo);val target=cards.createAlbum("目标")
        val old=cards.create(PolaroidCard(title="目标原照片"),target.id)
        val a=cards.create(PolaroidCard(title="A"));val b=cards.create(PolaroidCard(title="B"))
        cards.moveCards(listOf(a.id,b.id),target.id)
        assertEquals(listOf(a.id,b.id,old.id),cards.cardsInAlbum(target.id).map {it.id})
        cards.orderCards(target.id,listOf(old.id,b.id,a.id));cards.moveCards(listOf(a.id,b.id),target.id)
        assertEquals(listOf(old.id,b.id,a.id),cards.cardsInAlbum(target.id).map {it.id})
        val before=repo.snapshot.value
        assertTrue(runCatching {cards.removeCards(listOf(a.id,"missing"))}.isFailure);assertEquals(before,repo.snapshot.value)
        cards.removeCards(listOf(a.id,b.id));assertEquals(listOf(old),cards.cardsInAlbum(target.id))
        assertEquals(repo.snapshot.value,StickerRepository(repo.context,repo.paths,repo.media).snapshot.value)
    }
    @Test fun oldUndatedCatalogKeepsItsHandOrderAndOnlyLaterInsertionsReceiveRealDates() {
        val repo=repository();val cards=CardRepository(repo.context,repo)
        val a=cards.create(PolaroidCard(title="旧一"));val b=cards.create(PolaroidCard(title="旧二"))
        cards.orderCards(CardRepository.DEFAULT_ALBUM,listOf(a.id,b.id))
        val file=File(repo.root,"catalog.json");val json=JSONObject(file.readText());json.remove("cardCreatedAt");file.writeText(json.toString())
        val restored=StickerRepository(repo.context,repo.paths,repo.media);val old=CardRepository(repo.context,restored)
        assertEquals(0L,old.createdAt(a.id));assertEquals(0L,old.createdAt(b.id));assertEquals(listOf(a.id,b.id),old.cardsInAlbum(CardRepository.DEFAULT_ALBUM).map {it.id})
        val before=System.currentTimeMillis();val c=old.create(PolaroidCard(title="新"))
        assertEquals(listOf(c.id,a.id,b.id),old.cardsInAlbum(CardRepository.DEFAULT_ALBUM).map {it.id});assertTrue(old.createdAt(c.id) in before..System.currentTimeMillis())
        val again=CardRepository(repo.context,StickerRepository(repo.context,repo.paths,repo.media))
        assertEquals(0L,again.createdAt(a.id));assertEquals(old.createdAt(c.id),again.createdAt(c.id))
    }
    @Test fun failedCatalogWriteCannotPublishANewDateAndUnknownMetadataVersionDoesNotDiscardPhotos() {
        val setup=repository();val repo=StickerRepository(setup.context,setup.paths,setup.media,catalogByteLimit=2048)
        val cards=CardRepository(repo.context,repo);val existing=cards.create(PolaroidCard(title="保留"));val date=cards.createdAt(existing.id)
        val before=repo.snapshot.value;val tooLarge=PolaroidCard(title="新",description="字".repeat(8192))
        assertTrue(runCatching {cards.create(tooLarge)}.isFailure)
        assertEquals(before,repo.snapshot.value);assertEquals(date,cards.createdAt(existing.id));assertEquals(0L,cards.createdAt(tooLarge.id))
        val file=File(repo.root,"catalog.json");val json=JSONObject(file.readText());json.put("cardCreatedAtSchema",999);file.writeText(json.toString())
        val restored=StickerRepository(repo.context,repo.paths,repo.media)
        assertEquals(before,restored.snapshot.value);assertEquals(0L,restored.cardCreatedAt(existing.id))
        assertFalse(repo.root.listFiles().orEmpty().any {it.name.startsWith("catalog-recovery-")})
    }
}
