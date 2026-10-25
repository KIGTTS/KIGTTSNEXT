package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.Color
import com.kigtts.next.theme.gaussianBlur
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class GaussianBackgroundBlurTest {
    @Test fun ditherIsStableUnbiasedAndDoesNotTouchSourcePixels() {
        val source = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(127, 127, 127)) }
        val a = gaussianBlur(source, 3f); val b = gaussianBlur(source, 3f)
        try {
            val values = IntArray(96 * 96).also { a.getPixels(it, 0, 96, 0, 0, 96, 96) }
            val again = IntArray(values.size).also { b.getPixels(it, 0, 96, 0, 0, 96, 96) }
            assertArrayEquals(values, again)
            assertTrue(values.map(Color::red).toSet().size >= 2)
            assertEquals(127.0, values.map(Color::red).average(), .04)
            values.forEach { assertEquals(Color.red(it), Color.green(it)); assertEquals(Color.green(it), Color.blue(it)); assertTrue(Color.red(it) in 126..128) }
            assertEquals(Color.rgb(127, 127, 127), source.getPixel(0, 0))
        } finally { a.recycle(); b.recycle(); source.recycle() }
    }
    @Test fun convolutionUsesLinearLightAndRetainsFractionalPrecisionUntilFinalQuantization() {
        val source = Bitmap.createBitmap(65, 65, Bitmap.Config.ARGB_8888)
        for (y in 0..64) for (x in 0..64) source.setPixel(x, y, if (x < 32) Color.BLACK else Color.WHITE)
        val result = gaussianBlur(source, 4f)
        try {
            // A gamma-space 8-bit blur gives about 140; correct linear-light integration is ~195.
            assertTrue(Color.red(result.getPixel(32, 32)) in 185..205)
            assertEquals(255, Color.alpha(result.getPixel(32, 32)))
        } finally { source.recycle(); result.recycle() }
    }
}
