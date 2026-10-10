package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.media.*
import com.kigtts.next.media.thumbnail.*
import com.kigtts.plugin.runtime.PluginPaths
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MediaThumbnailTest {
    private lateinit var repository: MediaRepository
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        repository = MediaRepository(app, PluginPaths(app))
        repository.entries.value.forEach { repository.remove(it.id) }
    }
    @After fun cleanup() { repository.entries.value.forEach { repository.remove(it.id) } }
    private fun image(color: Int = 0xff009aa7.toInt()): MediaEntry {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File(app.cacheDir, "thumbnail-source.png")
        Bitmap.createBitmap(320, 192, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color); file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        return repository.import(Uri.fromFile(file), "image/png")
    }
    @Test fun repeatedAndConcurrentConsumersSharePixelsAndFileEditsInvalidateCache() = runBlocking {
        val entry = image()
        val pair = coroutineScope { List(2) { async { repository.thumbnails.thumbnail(entry, 320, 160) } }.awaitAll() }
        assertNotNull(pair[0]); assertSame(pair[0], pair[1])
        assertSame(pair[0], repository.thumbnails.cached(entry, 320, 160))
        assertSame(pair[0], repository.thumbnails.latest(entry))
        val file = repository.file(entry)!!; val stamp = file.lastModified()
        Bitmap.createBitmap(320, 192, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xffff6600.toInt()); file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }; file.setLastModified(stamp + 2000)
        val changed = repository.thumbnails.thumbnail(entry, 320, 160)!!
        assertNotSame(pair[0], changed)
        assertTrue(android.graphics.Color.red(changed.getPixel(280, 80)) > 200)
        repository.remove(entry.id); assertNull(repository.thumbnails.cached(entry, 320, 160)); assertNull(repository.thumbnails.latest(entry))
    }
    @Test fun webNeedsDisplayedSnapshotAndPluginPreviewsWriteNoExternalResources() = runBlocking {
        val entry = repository.html("网页", "<h1>预览</h1>")
        assertNull(repository.thumbnails.thumbnail(entry, 320, 160))
        val before = repository.root.walkTopDown().filter { it.isFile }.map { it.relativeTo(repository.root).path }.toList()
        val snapshot = Bitmap.createBitmap(320, 192, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff3366ff.toInt()) }
        repository.thumbnails.rememberWeb(entry, snapshot)
        val thumbnail = repository.thumbnails.thumbnail(entry, 320, 160)
        assertNotNull(thumbnail)
        val after = repository.root.walkTopDown().filter { it.isFile }.map { it.relativeTo(repository.root).path }.toList()
        assertEquals(before, after)
        val edited = repository.html("网页", "<h1>更新</h1>", entry.id)
        assertNull("The prior document snapshot cannot stand in for edited HTML", repository.thumbnails.source(edited))
    }
    @Test fun framingPersistsIndependentlyAndOldCatalogsKeepDefault() {
        val entry = image(); repository.thumbnail(entry.id, ThumbnailMode.FOCUS, .8, .3)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val saved = MediaRepository(app, PluginPaths(app)).get(entry.id)!!
        assertEquals(ThumbnailMode.FOCUS, saved.thumbnailMode); assertEquals(.8, saved.focusX, 0.0); assertEquals(.3, saved.focusY, 0.0)
        assertEquals(MediaScale.FIT, saved.scale)
        val old = entry.json().apply { remove("thumbnailMode"); remove("focusX"); remove("focusY") }
        assertEquals(ThumbnailMode.AUTO, MediaEntry.read(old).thumbnailMode)
    }
    @Test fun blurFallsOffTowardSharpRightRegionAndSizeBucketsRemainBounded() {
        val source = Bitmap.createBitmap(IntArray(160 * 160) { i ->
            if ((i % 160 / 4 + i / 160 / 4) % 2 == 0) 0xffffffff.toInt() else 0xff000000.toInt()
        }, 160, 160, Bitmap.Config.ARGB_8888)
        val entry = MediaEntry("fixture", "棋盘", MediaKind.IMAGE, "fixture", thumbnailMode = ThumbnailMode.FOCUS)
        val rendered = ThumbnailRenderer.render(source, 320, 160, entry)
        fun variation(start: Int, end: Int): Double = (start until end).sumOf { x ->
            abs(android.graphics.Color.red(rendered.getPixel(x, 80)) - android.graphics.Color.red(rendered.getPixel(x + 1, 80)))
        }.toDouble() / (end - start)
        assertTrue("Left is progressively blurred; the visual region retains edges", variation(250, 310) > variation(20, 80) * 4)
        assertEquals(MediaThumbnails.size(320, 160), MediaThumbnails.size(324, 163))
        assertTrue(MediaThumbnails.size(4000, 2400).first <= 384 && MediaThumbnails.size(4000, 2400).second <= 256)
        source.recycle(); rendered.recycle()
    }
}
