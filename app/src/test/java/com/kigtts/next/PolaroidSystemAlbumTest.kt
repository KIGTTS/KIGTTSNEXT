package com.kigtts.next

import android.app.Application
import android.content.ContextWrapper
import android.graphics.*
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.*
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PolaroidSystemAlbumTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var repo: StickerRepository
    private lateinit var publication: Publication
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val external = temporary.newFolder("private")
        val context = object : ContextWrapper(app) { override fun getExternalFilesDir(type: String?) = external }
        context.getSharedPreferences("polaroid-system-album", 0).edit().clear().commit()
        val paths = PluginPaths(context)
        repo = StickerRepository(context, paths, MediaRepository(context, paths)); publication = Publication()
    }
    private fun photo(): PolaroidCard {
        val source = temporary.newFile("source.png")
        val bitmap = Bitmap.createBitmap(3200, 2400, Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.RED)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val image = repo.importImage(Uri.fromFile(source))
        return PolaroidCard(imageId = image.id, title = "你好").also { repo.apply(listOf(StickerEdit.PutCard(it))) }
    }
    private fun album(render: suspend (PolaroidCard) -> File = { PolaroidRenderer.export(repo, it, maximumHeight = galleryPolaroidHeight(repo, it)) }) =
        PolaroidSystemAlbum(repo.context, repo, publication, render) { true }
    @Test fun savedEditsReplaceTheGalleryCopyWithoutChangingOriginalOrDuplicatingOnRestart() = runBlocking {
        val card = photo(); val original = repo.imageFile(card.imageId!!)!!.readBytes(); val album = album()
        assertEquals(0, album.synchronize()); assertEquals(1, publication.published.size)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(publication.published.first(), 0, publication.published.first().size, bounds)
        assertTrue(bounds.outHeight >= 3000); assertTrue(bounds.outWidth.toLong() * bounds.outHeight <= 12_010_000)
        val first = publication.published.first(); val updated = card.copy(description = "更新的说明", layers = listOf(CardLayer(kind = CardLayerKind.TEXT, text = "贴纸", color = 0xFF00FF00)))
        repo.apply(listOf(StickerEdit.PutCard(updated))); assertEquals(0, album.synchronize())
        assertEquals(2, publication.published.size); assertFalse(first.contentEquals(publication.published.last()))
        assertEquals(listOf(Uri.parse("content://media/external/images/media/1")), publication.removed)
        assertEquals(0, album().synchronize()); assertEquals(2, publication.published.size)
        assertArrayEquals(original, repo.imageFile(card.imageId!!)!!.readBytes()); assertEquals(updated, repo.cards.value.single())
        repo.apply(listOf(StickerEdit.RemoveCards(listOf(card.id))))
        album.synchronize(); assertEquals("Removing the in-app draft retains the exported photo", 1, publication.removed.size)
    }
    @Test fun failedAndObsoleteExportsDoNotRemoveTheLastGoodPhotoAndManualSaveCanRetry() = runBlocking {
        val card = photo(); val album = album(); album.synchronize()
        val edited = card.copy(title = "新标题"); repo.apply(listOf(StickerEdit.PutCard(edited)))
        publication.fail = true; assertEquals(1, album.synchronize()); assertTrue(publication.removed.isEmpty())
        publication.fail = false
        val stale = album { current -> repo.apply(listOf(StickerEdit.PutCard(current.copy(title = "又改了")))); File(repo.root, "unused.png") }
        assertEquals(0, stale.synchronize()); assertEquals(1, publication.published.size)
        album.save(card.id); assertEquals(2, publication.published.size); assertEquals(1, publication.removed.size)
        album.save(card.id); assertEquals(2, publication.published.size)
        publication.missing = true; album.save(card.id); assertEquals(3, publication.published.size)
    }
    @Test fun lowStrengthBloomCanExportToTheGalleryAndKeepsTheOriginal() = runBlocking {
        val card = photo().copy(photoStyle = PolaroidPhotoStyle(bloom = .12f))
        repo.apply(listOf(StickerEdit.PutCard(card)))
        val original = repo.imageFile(card.imageId!!)!!.readBytes()
        assertEquals(0, album().synchronize())
        assertEquals(1, publication.published.size)
        assertEquals(0, album().synchronize())
        assertEquals(1, publication.published.size)
        assertArrayEquals(original, repo.imageFile(card.imageId!!)!!.readBytes())
    }
    @Test fun cameraJpegKeepsOriginalBytesAndWatermarkAddsSpaceOutsideAllFourCorners() = runBlocking {
        val source = temporary.newFile("camera.jpg")
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val canvas = Canvas(bitmap)
        colors.forEachIndexed { i, color ->
            val x = i % 2 * 400f; val y = i / 2 * 300f
            canvas.drawRect(x, y, x + 400f, y + 300f, Paint().apply { this.color = color })
        }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 98, it) }; bitmap.recycle()
        val image = repo.importImage(Uri.fromFile(source))
        val card = PolaroidCard(title = "", imageId = image.id, frameStyleId = PhotoFrameSpec(800, 600).id)
        val album = album()
        album.encodings.set(card.id, CameraPhotoEncoding.JPEG)
        repo.apply(listOf(StickerEdit.PutCard(card)))
        album.save(card.id)
        assertArrayEquals(source.readBytes(), publication.published.single())

        val spec = PhotoFrameSpec(800, 600, "classic")
        repo.apply(listOf(StickerEdit.PutCard(card.copy(frameStyleId = spec.id))))
        album.save(card.id)
        val data = publication.published.last()
        assertEquals(0xff, data[0].toInt() and 255)
        assertEquals(0xd8, data[1].toInt() and 255)
        val result = requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size))
        try {
            val g = spec.geometry(); val scale = result.height / g.paperHeight
            assertEquals(4.0 / 3, (g.photoRight - g.photoLeft) / (g.photoBottom - g.photoTop), .001)
            assertTrue(g.paperWidth > g.photoRight && g.paperHeight > g.photoBottom)
            colors.forEachIndexed { i, expected ->
                val x = if (i % 2 == 0) g.photoLeft + 1 else g.photoRight - 1
                val y = if (i < 2) g.photoTop + 1 else g.photoBottom - 1
                val pixel = result.getPixel((x * scale).toInt(), (y * scale).toInt())
                for (shift in listOf(0, 8, 16))
                    assertTrue(kotlin.math.abs((pixel shr shift and 255) - (expected shr shift and 255)) <= 8)
            }
            assertArrayEquals(source.readBytes(), repo.imageFile(image.id)!!.readBytes())
        } finally { result.recycle() }
    }
    private class Publication : GalleryImagePublication {
        val published = mutableListOf<ByteArray>(); val removed = mutableListOf<Uri>(); var fail = false; var missing = false
        override fun publish(image: File, folder: String, takenAt: Long): Uri {
            check(!fail) { "保存失败" }; published += image.readBytes()
            return Uri.parse("content://media/external/images/media/${published.size}")
        }
        override fun remove(uri: Uri) { removed += uri }
        override fun exists(uri: Uri) = !missing
    }
}
