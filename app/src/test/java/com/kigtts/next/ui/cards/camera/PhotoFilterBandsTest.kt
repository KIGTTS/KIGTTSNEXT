package com.kigtts.next.ui.cards.camera

import android.graphics.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PhotoFilterBandsTest {
    @Test fun nativeBandsMatchTheLiveMatrixIncludingFinalPartialBandWithoutResizing() = runTest {
        for (filter in PhotoFilter.entries.filter { it != PhotoFilter.ORIGINAL }) for (strength in listOf(.5f, 1f)) {
            val original = Bitmap.createBitmap(79, 73, Bitmap.Config.ARGB_8888)
            val expected = Bitmap.createBitmap(79, 73, Bitmap.Config.ARGB_8888)
            var actual: Bitmap? = null
            try {
                for (y in 0 until original.height) for (x in 0 until original.width)
                    original.setPixel(x, y, Color.rgb((x * 11) % 256, (y * 13) % 256, ((x + y) * 7) % 256))
                val sourcePixels = IntArray(79 * 73).also { original.getPixels(it, 0, 79, 0, 0, 79, 73) }
                Canvas(expected).drawBitmap(original, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(PhotoFilters.matrix(filter, strength)) })
                val mutable = requireNotNull(original.copy(Bitmap.Config.ARGB_8888, true))
                actual = mutable
                applyPhotoFilterBands(mutable, filter, strength)
                assertEquals(79, mutable.width); assertEquals(73, mutable.height)
                val expectedPixels = IntArray(79 * 73).also { expected.getPixels(it, 0, 79, 0, 0, 79, 73) }
                val actualPixels = IntArray(79 * 73).also { mutable.getPixels(it, 0, 79, 0, 0, 79, 73) }
                assertArrayEquals(filter.name + "/" + strength, expectedPixels, actualPixels)
                assertArrayEquals(sourcePixels, IntArray(79 * 73).also { original.getPixels(it, 0, 79, 0, 0, 79, 73) })
            } finally { actual?.recycle(); expected.recycle(); original.recycle() }
        }
    }
    @Test fun cancelledProcessingDoesNotWriteAFirstBandAndOriginalModeLeavesPixelsUntouched() = runTest {
        val image = Bitmap.createBitmap(96, 77, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        try {
            applyPhotoFilterBands(image, PhotoFilter.ORIGINAL)
            assertEquals(Color.MAGENTA, image.getPixel(0, 0))
            var entered = false
            val failure = runCatching { withContext(Job()) {
                entered = true
                currentCoroutineContext().cancel()
                applyPhotoFilterBands(image, PhotoFilter.BW)
            } }.exceptionOrNull()
            assertTrue(entered); assertTrue(failure is CancellationException)
            assertEquals(Color.MAGENTA, image.getPixel(0, 0)); assertEquals(Color.MAGENTA, image.getPixel(95, 76))
        } finally { image.recycle() }
    }
}
