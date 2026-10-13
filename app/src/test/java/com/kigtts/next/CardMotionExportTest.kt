package com.kigtts.next

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.media.MotionPhoto
import com.kigtts.next.media.MotionPhotoWriter
import com.kigtts.next.ui.cards.camera.PhotoFilter
import com.kigtts.next.ui.cards.camera.PhotoFilters
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CardMotionExportTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var stickers: StickerRepository
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val external = temporary.newFolder("private")
        val context = object : ContextWrapper(app) { override fun getExternalFilesDir(type: String?) = external }
        val paths = PluginPaths(context); stickers = StickerRepository(context, paths, MediaRepository(context, paths))
    }
    private fun image(color: Int): String {
        val file = temporary.newFile()
        val bitmap = Bitmap.createBitmap(96, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        return stickers.importResource(file).id
    }
    private fun design() = PolaroidCard(title = "纸框与文字", description = "贴纸随实况导出", imageId = image(Color.BLUE), frameStyleId = "classic",
        layers = listOf(CardLayer(kind = CardLayerKind.IMAGE, imageId = image(Color.RED), x = .5, y = .35, width = .18)))
    private fun video(): File = temporary.newFile("motion.mp4").apply {
        CardMotionExportTest::class.java.classLoader!!.getResourceAsStream("motion/blue.mp4")!!.use { source -> outputStream().use { source.copyTo(it) } }
        ShadowMediaMetadataRetriever.addMetadata(absolutePath, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "96")
    }

    @Test fun staticAndTransparentMotionOverlaysSharePaperStickerAndFramelessCropGeometryWithoutEditingTheDesign() {
        val card = design(); val revision = stickers.snapshot.value.revision
        val still = PolaroidRenderer.renderBitmap(stickers, card)
        val overlay = PolaroidRenderer.renderBitmap(stickers, card, transparentPhoto = true)
        val plain = PolaroidRenderer.renderBitmap(stickers, card, PolaroidExportOptions(includeStickers = false))
        val frameless = PolaroidRenderer.renderBitmap(stickers, card, PolaroidExportOptions(includeFrame = false))
        try {
            assertEquals(1200, still.width); assertEquals(1500, still.height)
            val x = still.width / 2; val y = (still.height * .35f).toInt()
            assertTrue(Color.red(still.getPixel(x, y)) > 240 && Color.blue(still.getPixel(x, y)) < 10)
            assertEquals(still.getPixel(x, y), overlay.getPixel(x, y))
            assertTrue(Color.blue(plain.getPixel(x, y)) > 240 && Color.red(plain.getPixel(x, y)) < 10)
            assertEquals(0, Color.alpha(overlay.getPixel((still.width * .3f).toInt(), y)))
            assertEquals(Color.WHITE, overlay.getPixel(12, 12))
            assertEquals(1072, frameless.width); assertEquals(1128, frameless.height)
            assertEquals(revision, stickers.snapshot.value.revision)
        } finally { listOf(still, overlay, plain, frameless).forEach { it.recycle() } }
    }

    @Test fun motionPhotoContainerRoundTripsARealEncodedClipAndItsCompleteDecoratedJpeg() {
        val card = design(); val bitmap = PolaroidRenderer.renderBitmap(stickers, card)
        val cover = temporary.newFile("cover.jpg")
        try { cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } } finally { bitmap.recycle() }
        val clip = video(); val output = File(temporary.root, "decoratedMP.jpg")
        MotionPhotoWriter.write(cover, clip, output, 500000)
        val container = requireNotNull(MotionPhoto.locate(output))
        assertEquals(clip.length(), container.length)
        val extracted = temporary.newFile("extracted.mp4")
        assertTrue(MotionPhoto.extract(output, extracted)); assertArrayEquals(clip.readBytes(), extracted.readBytes())
        val jpeg = requireNotNull(android.graphics.BitmapFactory.decodeFile(output.absolutePath))
        try {
            assertEquals(1200, jpeg.width); assertEquals(1500, jpeg.height)
            val pixel = jpeg.getPixel(jpeg.width / 2, (jpeg.height * .35f).toInt())
            assertTrue(Color.red(pixel) > 220 && Color.blue(pixel) < 30)
        } finally { jpeg.recycle() }
        val prefix = output.inputStream().use { String(it.readNBytes(4096), Charsets.ISO_8859_1) }
        assertTrue(prefix.contains("MotionPhotoVersion=\"1\"")); assertTrue(prefix.contains("MotionPhotoPresentationTimestampUs=\"500000\""))
        assertThrows(IllegalArgumentException::class.java) { MotionPhotoWriter.write(cover, clip, File(temporary.root, "bad.jpg")) }
    }

    @Test fun immutableImageAttachmentSurvivesSourceDeletionAndAnUnsavedRetakeNeverOverridesIt() {
        val originalImage = image(Color.BLUE); val motion = CardMotionRepository(stickers)
        val clip = video(); val bytes = clip.readBytes(); val matrix = PhotoFilters.coefficients(PhotoFilter.WARM, .5f)
        val saved = motion.import(originalImage, clip, 1000, 0, 500, matrix)
        clip.delete()
        val restored = requireNotNull(CardMotionRepository(stickers).get(originalImage))
        assertArrayEquals(bytes, restored.video.readBytes()); assertArrayEquals(matrix, restored.colorMatrix, .00001f)
        val retake = image(Color.GREEN)
        assertNull(motion.get(retake)); assertEquals(saved.video, motion.get(originalImage)?.video)
        assertThrows(IllegalArgumentException::class.java) { motion.import(originalImage, restored.video, 1000) }
        stickers.apply(listOf(StickerEdit.RemoveImages(listOf(originalImage))))
        motion.prune(); assertFalse(restored.video.exists()); assertNull(motion.get(originalImage))
    }
}
