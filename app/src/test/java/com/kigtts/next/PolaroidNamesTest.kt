@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.CardRepository
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.plugins.stickers.StickerHost
import com.kigtts.next.ui.cards.CardMenuAction
import com.kigtts.next.ui.cards.CardPhotoSelection
import com.kigtts.next.ui.cards.CardWorkspaceActions
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
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
@Config(sdk = [35])
class PolaroidNamesTest {
    private class Fixture {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val paths = PluginPaths(app)
        val media = MediaRepository(app, paths)
        val stickers: StickerRepository
        val cards: CardRepository
        val opened = mutableListOf<String>()
        val messages = mutableListOf<String>()
        init {
            val external = requireNotNull(app.getExternalFilesDir(null)).canonicalFile
            val root = File(external, "appdata/Kigtts/cards").canonicalFile
            check(root.toPath().startsWith(external.toPath()) && root != external)
            root.deleteRecursively()
            stickers = StickerRepository(app, paths, media)
            cards = CardRepository(app, stickers)
        }
        fun broker(repository: StickerRepository = stickers) = StickerHost(repository, paths)
            .forPlugin("test.names", setOf("stickers-read", "stickers-write"))
        fun photo(): File {
            val source = File(app.cacheDir, "optional-name-${UUID.randomUUID()}.png")
            val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            try { source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
            return source
        }
    }

    @Test fun pluginCanCreateABlankPolaroidAndRestartPreservesBothNamedAndUnnamedCards() = runBlocking {
        val f = Fixture()
        val named = f.cards.create(PolaroidCard(title = "保留旧名称"))
        val unnamed = PolaroidCard(title = "", description = "说明仍保留")
        val broker = f.broker()
        val plan = broker.request(StickerCommand.Prepare("empty-name", f.stickers.snapshot.value.revision,
            listOf(StickerEdit.PutCard(unnamed)))) as StickerReply.Prepared
        assertTrue(broker.request(StickerCommand.Commit(plan.value.token)) is StickerReply.Applied)
        val restored = StickerRepository(f.app, f.paths, f.media)
        val queried = (f.broker(restored).request(StickerCommand.Query(StickerScope.CARDS)) as StickerReply.Snapshot).value.cards
        assertEquals(named, queried.single { it.id == named.id })
        assertEquals(unnamed, queried.single { it.id == unnamed.id })
        assertEquals("", queried.single { it.id == unnamed.id }.title)
        assertNull(queried.single { it.id == unnamed.id }.imageId)
        assertTrue(queried.single { it.id == unnamed.id }.layers.isEmpty())
    }

    @Test fun optionalTitleRetainsLengthAndNulLimitsWithoutRelaxingLibraryNames() = runBlocking {
        val f = Fixture()
        val allowed = f.cards.create(PolaroidCard(title = "字".repeat(128)))
        val broker = f.broker()
        val before = f.stickers.snapshot.value
        for ((index, badTitle) in listOf("字".repeat(129), "名称\u0000坏字符").withIndex()) {
            val result = broker.request(StickerCommand.Prepare("invalid-$index", before.revision,
                listOf(StickerEdit.PutCard(PolaroidCard(title = "")),
                    StickerEdit.PutCard(allowed.copy(title = badTitle))))) as StickerReply.Failure
            assertEquals(StickerError.INVALID, result.code)
            assertEquals(before, f.stickers.snapshot.value)
        }
        val result = broker.request(StickerCommand.Prepare("empty-library", before.revision,
            listOf(StickerEdit.PutLibrary(StickerLibrary(name = ""))))) as StickerReply.Failure
        assertEquals(StickerError.INVALID, result.code)
        assertEquals(before, f.stickers.snapshot.value)
    }

    @Test fun importedAndStickerPhotosStartUnnamedWithoutChangingTheirSources() = runTest {
        val f = Fixture()
        val album = f.cards.createAlbum("选中的相册")
        val actions = CardWorkspaceActions(f.app, f.cards, f.stickers, this, { f.opened += it },
            { f.messages += it }, { _, _ -> }, StandardTestDispatcher(testScheduler))
        val source = f.photo()
        var releases = 0
        try {
            actions.importPhoto(CardPhotoSelection(Uri.fromFile(source)) { releases++; source.delete() }, albumId = album.id)
            testScheduler.advanceUntilIdle()
            assertEquals(1, releases)
            assertFalse(source.exists())
            val imported = f.cards.cardsInAlbum(album.id).single { it.imageId != null }
            val item = StickerItem(libraryId = StickerRepository.DEFAULT_LIBRARY, name = "贴纸名称保留",
                imageId = requireNotNull(imported.imageId))
            f.stickers.apply(listOf(StickerEdit.PutItem(item)))
            actions.stickerCard(item)
            testScheduler.advanceUntilIdle()
            assertEquals(2, f.cards.cards.value.size)
            assertTrue(f.cards.cards.value.all { it.title.isEmpty() })
            assertEquals(2, f.opened.size)
            assertTrue(f.messages.isEmpty())
            assertEquals(item, f.stickers.items.value.single())
            assertTrue(requireNotNull(f.stickers.imageFile(item.imageId)).isFile)
            assertFalse(actions.busy)
        } finally { source.delete() }
    }

    @Test fun copyingUnnamedPhotosKeepsThemUnnamedAndNamedCopiesKeepTheirSuffixAndAlbum() = runTest {
        val f = Fixture()
        val album = f.cards.createAlbum("复制相册")
        val named = f.cards.create(PolaroidCard(title = "旅行", description = "原来的内容"), album.id)
        val unnamed = f.cards.create(PolaroidCard(title = "", description = "无名内容"), album.id)
        val actions = CardWorkspaceActions(f.app, f.cards, f.stickers, this, { f.opened += it },
            { f.messages += it }, { _, _ -> }, StandardTestDispatcher(testScheduler))
        actions.cardAction(named, CardMenuAction.COPY)
        testScheduler.advanceUntilIdle()
        actions.cardAction(unnamed, CardMenuAction.COPY)
        testScheduler.advanceUntilIdle()
        val copies = f.cards.cardsInAlbum(album.id).filter { it.id != named.id && it.id != unnamed.id }
        assertEquals(2, copies.size)
        assertEquals("旅行 副本", copies.single { it.description == named.description }.title)
        assertEquals("", copies.single { it.description == unnamed.description }.title)
        assertEquals(named, f.cards.cards.value.single { it.id == named.id })
        assertEquals(unnamed, f.cards.cards.value.single { it.id == unnamed.id })
        assertFalse(actions.busy)
    }

    @Test fun exportingAnUnnamedPhotoDoesNotPaintAnAutomaticNameOnItsPaper() {
        val f = Fixture()
        val card = f.cards.create(PolaroidCard(title = "", frameStyleId = "classic", frameColor = 0xFFFFFFFFL))
        val output = f.stickers.exportCard(card.id)
        val bitmap = output.inputStream().use { requireNotNull(BitmapFactory.decodeStream(it)) }
        try {
            // The title rectangle is below the photo and to the left of the existing logo.
            for (y in 1260 until 1320 step 3) for (x in 80 until 700 step 3) {
                assertEquals("An unnamed title must leave the paper blank at $x,$y", Color.WHITE, bitmap.getPixel(x, y))
            }
            assertEquals("${card.id}.png", output.name)
        } finally { bitmap.recycle() }
    }
}
