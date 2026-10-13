package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
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
@Config(sdk = [35])
class CardCaptureAlbumTest {
    @Test fun capturedPhotoBelongsToTheAlbumSelectedWhenTheCameraOpened() = runBlocking {
        val (cards, stickers) = repositories(); val selected = cards.createAlbum("本次拍摄")
        val other = cards.createAlbum("其它照片"); var current = selected.id
        val state = MutableStateFlow(AppState(destination = Destination.Cards))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = coordinator(cards, stickers, scope, state) { current }
        val source = photo()
        try {
            coordinator.open(); val destination = state.value.destination as Destination.CardCamera
            current = other.id
            coordinator.captured(destination, source, source, PolaroidFormat.MINI)
            waitForSave(state, source)
            assertEquals(1, cards.cardsInAlbum(selected.id).size); assertTrue(cards.cardsInAlbum(other.id).isEmpty())
            val saved = cards.cardsInAlbum(selected.id).single()
            assertEquals("", saved.title)
            assertEquals(saved.originalImageId, saved.imageId); assertEquals("instax.mini.classic", saved.frameStyleId)
        } finally { scope.cancel(); source.delete() }
    }

    @Test fun removedAlbumFallsBackToDefaultWithoutLosingTheCapturedImage() = runBlocking {
        val (cards, stickers) = repositories(); val selected = cards.createAlbum("临时相册")
        val before = cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM).size
        val state = MutableStateFlow(AppState(destination = Destination.Cards))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = coordinator(cards, stickers, scope, state) { selected.id }
        val source = photo()
        try {
            coordinator.open(); val destination = state.value.destination as Destination.CardCamera
            cards.removeAlbum(selected.id)
            coordinator.captured(destination, source, source, PolaroidFormat.SQUARE)
            waitForSave(state, source)
            assertEquals(before + 1, cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM).size)
            val saved = cards.cardsInAlbum(CardRepository.DEFAULT_ALBUM).last()
            assertTrue(requireNotNull(stickers.imageFile(saved.imageId!!)).isFile)
        } finally { scope.cancel(); source.delete() }
    }

    private fun repositories(): Pair<CardRepository, StickerRepository> {
        val app = ApplicationProvider.getApplicationContext<Application>(); val paths = PluginPaths(app)
        val stickers = StickerRepository(app, paths, MediaRepository(app, paths))
        return CardRepository(app, stickers) to stickers
    }
    private fun coordinator(cards: CardRepository, stickers: StickerRepository, scope: CoroutineScope,
        state: MutableStateFlow<AppState>, album: () -> String) = CardCaptureCoordinator(cards, stickers, scope, { state.value },
        { state.value = state.value.copy(destination = it) }, { state.value = state.value.copy(destination = Destination.Cards) },
        { fail(it) }, selectedAlbum = album)
    private suspend fun waitForSave(state: MutableStateFlow<AppState>, source: File) {
        withTimeout(5_000) { while (state.value.destination != Destination.Cards || source.exists()) delay(10) }
    }
    private fun photo(): File {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File(app.cacheDir, "capture-album-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(46, 62, Bitmap.Config.ARGB_8888)
        try { bitmap.eraseColor(android.graphics.Color.CYAN); file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) } }
        finally { bitmap.recycle() }
        return file
    }
}
