package com.kigtts.next

import android.app.Application
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.media.color.CubeLut
import com.kigtts.next.state.*
import com.kigtts.next.ui.cards.camera.*
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
class CardPhotoStyleTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun repository(): StickerRepository { val paths = PluginPaths(app); return StickerRepository(app, paths, MediaRepository(app, paths)) }
    private fun source(): File = File(app.cacheDir, "recipe-${UUID.randomUUID()}.png").also { file ->
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(210, 60, 25)) }
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
    }
    private fun pixel(stickers: StickerRepository, card: PolaroidCard): Int {
        val rendered = PolaroidRenderer.renderBitmap(stickers, card, maximumHeight = 400)
        try { val geometry = polaroidPaperGeometry(card, stickers.frameStyle(card)); val unit = rendered.height / geometry.paperHeight
            return rendered.getPixel(((geometry.photoLeft + geometry.photoWidth / 2) * unit).toInt(), ((geometry.photoTop + geometry.photoHeight / 2) * unit).toInt())
        } finally { rendered.recycle() }
    }
    @Test fun captureStoresOneUntouchedOriginalAndTheRecipeSurvivesReopenAndRepeatedEditing() = runBlocking {
        val stickers = repository(); val cards = CardRepository(app, stickers)
        val state = MutableStateFlow(AppState(destination = Destination.Cards))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = CardCaptureCoordinator(cards, stickers, scope, { state.value },
            { state.value = state.value.copy(destination = it) }, { state.value = state.value.copy(destination = Destination.Cards) }, { fail(it) })
        val file = source(); val original = file.readBytes()
        try {
            coordinator.open(); coordinator.captured(state.value.destination as Destination.CardCamera, file, file, PolaroidFormat.SQUARE,
                photoStyle = CapturedPhotoStyle(PhotoFilter.BW, .7f))
            withTimeout(5000) { while (state.value.destination != Destination.Cards || file.exists()) delay(10) }
            val card = cards.cards.value.first(); assertEquals(card.imageId, card.originalImageId)
            assertEquals(PolaroidPhotoStyle("BW", .7f), card.photoStyle)
            assertArrayEquals(original, stickers.imageFile(card.imageId!!)!!.readBytes())
            val saved = card.copy(photoStyle = card.photoStyle!!.copy(strength = 1f)); cards.update(saved)
            val gray = pixel(stickers, saved); assertTrue(kotlin.math.abs(Color.red(gray) - Color.blue(gray)) <= 1)
            val plain = saved.withPhotoStyle(PolaroidPhotoStyle()); cards.update(plain)
            assertEquals(Color.rgb(210, 60, 25), pixel(stickers, plain))
            assertEquals(plain, repository().cards.value.first { it.id == card.id })
            assertArrayEquals(original, stickers.imageFile(card.originalImageId!!)!!.readBytes())
            val legacy = plain.copy(imageId = "legacy-filtered", photoStyle = null)
            assertEquals(card.originalImageId, legacy.withPhotoStyle(PolaroidPhotoStyle("BW")).imageId)
        } finally { scope.cancel(); file.delete() }
    }
    @Test fun burstKeepsItsJpegAndNeverCreatesFilteredTemporaryFiles() = runBlocking {
        val file = source(); val bytes = file.readBytes()
        val shot = PolaroidCameraCapture(file, null, PolaroidFormat.MINI, PhotoFilter.ORIGINAL)
        val styled = processBurstPhoto(shot, PhotoFilter.WARM, .4f, null, com.kigtts.next.media.color.PhotoEffects.NONE) { fail(it) }
        assertNull(styled.processed); assertEquals(file, styled.displayFile); assertEquals(.4f, styled.photoStyle!!.strength)
        assertArrayEquals(bytes, file.readBytes()); styled.discard()
    }
    @Test fun customLutIsPortableAndProtectedFromDeletionAndMotionUsesTheEditedRecipeOnce() {
        val stickers = repository(); val file = source()
        val lut = CubeLut.read("""LUT_3D_SIZE 2
            1 1 1
            0 1 1
            1 0 1
            0 0 1
            1 1 0
            0 1 0
            1 0 0
            0 0 0
        """.trimIndent().reader())
        try {
            val image = stickers.importResource(file)
            val style = CapturedPhotoStyle(PhotoFilter.CUSTOM, 1f, lut).persist(stickers)
            val card = PolaroidCard(title = "", imageId = image.id, originalImageId = image.id, photoStyle = style)
            CardRepository(app, stickers).create(card)
            val inverted = pixel(stickers, card)
            assertEquals(Color.rgb(45, 195, 230), inverted)
            try { stickers.apply(listOf(StickerEdit.RemoveImages(listOf(style.lutImageId!!)))); fail("Referenced LUT was deleted") } catch (_: StickerFault) { }
            val clip = CardMotionClip(image.id, file, 3000, 0, 1500, PhotoFilters.coefficients(PhotoFilter.WARM))
            val edited = clip.withPhotoStyle(PolaroidPhotoStyle("BW", .5f), stickers::imageFile)
            assertArrayEquals(PhotoFilters.coefficients(PhotoFilter.BW, .5f), edited.colorMatrix, 0f)
            assertNull(edited.lut); assertSame(clip, clip.withPhotoStyle(null, stickers::imageFile))
        } finally { file.delete() }
    }
}
