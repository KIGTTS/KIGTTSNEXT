package com.kigtts.next

import com.kigtts.next.i18n.uiCopy

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.state.*
import com.kigtts.next.ui.cards.camera.*
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CardMotionCaptureDeliveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun repositories(): Pair<CardRepository, StickerRepository> {
        val app = ApplicationProvider.getApplicationContext<Application>(); val paths = PluginPaths(app)
        val stickers = StickerRepository(app, paths, MediaRepository(app, paths))
        return CardRepository(app, stickers) to stickers
    }
    private fun photo(color: Int): File = temporary.newFile().also { file ->
        val bitmap = Bitmap.createBitmap(48, 62, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } } finally { bitmap.recycle() }
    }
    private fun motion(): CameraMotionClip {
        val file = temporary.newFile()
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("motion/blue.mp4")).use { source -> file.outputStream().use { source.copyTo(it) } }
        return CameraMotionClip(file, 1000, 0, 500, PhotoFilters.coefficients(PhotoFilter.WARM))
    }

    @Test fun deliveryAcknowledgementProtectsTheInFlightFilesAndAttachesMotionToTheDisplayImage() = runBlocking {
        val (cards, stickers) = repositories(); val prior = cards.cards.value.map { it.id }.toSet()
        val state = MutableStateFlow(AppState(destination = Destination.Cards)); val messages = CopyOnWriteArrayList<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val entered = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        val savedVideo = File(temporary.root, "owned.mp4"); var savedImage: String? = null
        val coordinator = CardCaptureCoordinator(cards, stickers, scope, { state.value },
            { state.value = state.value.copy(destination = it) }, { state.value = state.value.copy(destination = Destination.Cards) },
            messages::add, saveMotion = { imageId, clip ->
                savedImage = imageId; clip.video.copyTo(savedVideo)
                entered.complete(Unit); check(release.await(5, TimeUnit.SECONDS))
            })
        val original = photo(android.graphics.Color.RED); val display = photo(android.graphics.Color.GREEN); val clip = motion()
        try {
            coordinator.open(); val camera = state.value.destination as Destination.CardCamera
            assertTrue(coordinator.captured(camera, original, display, PolaroidFormat.SQUARE, motion = clip))
            withTimeout(5000) { entered.await() }
            assertTrue(coordinator.processing.value)
            coordinator.open(); assertEquals(camera, state.value.destination)
            assertTrue(stickers.context.uiCopy("camera.photo_saving_wait") in messages)
            assertFalse(coordinator.captured(camera, original, display, PolaroidFormat.SQUARE, motion = clip))
            assertTrue(original.isFile && display.isFile && clip.video.isFile)
            val rejected = photo(android.graphics.Color.BLACK)
            assertFalse(coordinator.captured(camera, rejected, rejected, PolaroidFormat.SQUARE))
            assertFalse(rejected.exists())
            release.countDown(); withTimeout(5000) { coordinator.processing.first { !it } }
            val card = cards.cards.value.single { it.id !in prior }
            assertEquals(card.imageId, savedImage); assertNotEquals(card.imageId, card.originalImageId)
            assertTrue(savedVideo.isFile); assertTrue(requireNotNull(stickers.imageFile(card.imageId!!)).isFile)
            assertFalse(original.exists()); assertFalse(display.exists()); assertFalse(clip.video.exists())
            assertEquals(Destination.Cards, state.value.destination)
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun failedMotionPersistenceRetainsThePhotoAndReleasesTheNativeBusyState() = runBlocking {
        val (cards, stickers) = repositories(); val prior = cards.cards.value.map { it.id }.toSet()
        val state = MutableStateFlow(AppState(destination = Destination.Cards)); val messages = CopyOnWriteArrayList<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = CardCaptureCoordinator(cards, stickers, scope, { state.value },
            { state.value = state.value.copy(destination = it) }, { state.value = state.value.copy(destination = Destination.Cards) },
            messages::add, saveMotion = { _, _ -> error("Storage unavailable") })
        val original = photo(android.graphics.Color.BLUE); val clip = motion()
        try {
            coordinator.open(); assertTrue(coordinator.captured(state.value.destination as Destination.CardCamera,
                original, original, PolaroidFormat.SQUARE, motion = clip))
            withTimeout(5000) { coordinator.processing.first { !it } }
            val card = cards.cards.value.single { it.id !in prior }
            assertTrue(requireNotNull(stickers.imageFile(card.imageId!!)).isFile)
            assertTrue(stickers.context.uiCopy("camera.motion_clip_save_failed") in messages)
            assertFalse(original.exists()); assertFalse(clip.video.exists()); assertEquals(Destination.Cards, state.value.destination)
            coordinator.open(); assertTrue(state.value.destination is Destination.CardCamera)
        } finally { scope.cancel() }
    }
}
