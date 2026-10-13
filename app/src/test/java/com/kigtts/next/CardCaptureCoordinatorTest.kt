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
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CardCaptureCoordinatorTest {
    private fun repositories(): Pair<CardRepository, StickerRepository> {
        val app = ApplicationProvider.getApplicationContext<Application>(); val paths = PluginPaths(app)
        val stickers = StickerRepository(app, paths, MediaRepository(app, paths))
        return CardRepository(app, stickers) to stickers
    }
    private fun photo(color: Int): File {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File(app.cacheDir, "capture-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(48, 62, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } } finally { bitmap.recycle() }
        return file
    }
    @Test fun duplicateDeliveryDoesNotDeleteAnInFlightFileOrCreateTwoDesigns() = runBlocking {
        val (cards, stickers) = repositories(); val beforeIds = cards.cards.value.map { it.id }.toSet(); val before = beforeIds.size
        val state = MutableStateFlow(AppState(destination = Destination.Cards))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = CardCaptureCoordinator(cards, stickers, scope, { state.value },
            { state.value = state.value.copy(destination = it) }, { state.value = state.value.copy(destination = Destination.Cards) }, { fail(it) })
        val source = photo(android.graphics.Color.BLUE)
        try {
            coordinator.open(); val destination = state.value.destination as Destination.CardCamera
            coordinator.captured(destination, source, source, PolaroidFormat.SQUARE)
            coordinator.captured(destination, source, source, PolaroidFormat.SQUARE)
            withTimeout(5_000) { while (state.value.destination != Destination.Cards || source.exists()) delay(10) }
            assertEquals(before + 1, cards.cards.value.size)
            val saved = cards.cards.value.single { it.id !in beforeIds }
            assertEquals("", saved.title)
            assertEquals(saved.imageId, saved.originalImageId); assertEquals("instax.square.classic", saved.frameStyleId)
        } finally { scope.cancel(); source.delete() }
    }
    @Test fun editorDeliveryPreservesTheSavedDesignAndRetainsBothImageResources() = runBlocking {
        val (cards, stickers) = repositories(); val saved = cards.create(PolaroidCard(title = "原标题"))
        val editor = Destination.CardEditor(saved.id); val state = MutableStateFlow(AppState(destination = editor))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = CardCaptureCoordinator(cards, stickers, scope, { state.value },
            { state.value = state.value.copy(destination = it) }, { state.value = state.value.copy(destination = editor) }, { fail(it) })
        val original = photo(android.graphics.Color.RED); val processed = photo(android.graphics.Color.GREEN)
        try {
            coordinator.open(saved.id); coordinator.captured(state.value.destination as Destination.CardCamera, original, processed, PolaroidFormat.WIDE)
            withTimeout(5_000) { while (coordinator.result.value == null || original.exists() || processed.exists()) delay(10) }
            val result = requireNotNull(coordinator.result.value)
            assertEquals(saved, cards.cards.value.first { it.id == saved.id }); assertEquals(saved.id, result.cardId)
            assertNotEquals(result.imageId, result.originalImageId)
            assertTrue(requireNotNull(stickers.imageFile(result.imageId)).isFile)
            assertTrue(requireNotNull(stickers.imageFile(result.originalImageId)).isFile)
            assertEquals("instax.wide.classic", result.frameStyleId); assertEquals(editor, state.value.destination)
        } finally { scope.cancel(); original.delete(); processed.delete() }
    }
}
