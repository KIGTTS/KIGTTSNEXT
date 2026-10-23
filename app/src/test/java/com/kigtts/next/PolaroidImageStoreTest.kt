package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.cards.PolaroidImageStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PolaroidImageStoreTest {
    private fun resource(): File = File.createTempFile("polaroid-cache-", ".png",
        ApplicationProvider.getApplicationContext<Application>().cacheDir).apply { writeText("source") }
    @Test fun cancelledConsumerDoesNotCancelSharedDecodeAndReopeningReusesTheSamePixels() = runBlocking {
        val file = resource(); val count = AtomicInteger(); val started = CountDownLatch(1); val release = CountDownLatch(1)
        val store = PolaroidImageStore(4096) { _, _ ->
            count.incrementAndGet(); started.countDown(); check(release.await(5, TimeUnit.SECONDS))
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        }
        try {
            val hidden = async { store.load(file, 64) }
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            val visible = async { store.load(file, 64) }
            hidden.cancelAndJoin(); release.countDown()
            val image = requireNotNull(visible.await())
            assertSame(image, store.load(file, 64)); assertSame(image, store.peek(file, 64))
            assertEquals(1, count.get())
        } finally { release.countDown(); store.close(); file.delete() }
    }
    @Test fun decodeBucketAndSourceReplacementInvalidateWhileEvictionNeverRecyclesVisiblePixels() = runBlocking {
        val file = resource(); val count = AtomicInteger()
        val store = PolaroidImageStore(512) { _, _ ->
            count.incrementAndGet(); Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        }
        try {
            val first = requireNotNull(store.load(file, 64))
            assertSame("A new layout must reuse any cached LOD before its sharper decode", first, store.preview(file, 256))
            assertNotSame(first, store.load(file, 128)); assertEquals(2, count.get())
            file.writeText("changed-source")
            assertNull("A replaced source must never reuse old pixels", store.preview(file, 256))
            assertNotSame(first, store.load(file, 64)); assertEquals(3, count.get())
            assertTrue(store.memoryBytes <= 512)
            assertFalse(first.isRecycled)
            store.close(); assertFalse(first.isRecycled)
        } finally { store.close(); file.delete() }
    }
    @Test fun closingDuringABlockingDecodeCannotRepopulateOrRestartTheCache() = runBlocking {
        val file = resource(); val count = AtomicInteger()
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val store = PolaroidImageStore(4096) { _, _ ->
            count.incrementAndGet(); started.countDown(); check(release.await(5, TimeUnit.SECONDS))
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        }
        try {
            val consumer = async { store.load(file, 64) }
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            store.close(); release.countDown()
            withTimeout(5000) { consumer.join() }
            assertEquals("A cancelled blocking decode must not refill the cache", 0, store.memoryBytes)
            assertNull(store.peek(file, 64)); assertNull(store.load(file, 64))
            assertEquals(1, count.get())
        } finally { release.countDown(); store.close(); file.delete() }
    }
}
