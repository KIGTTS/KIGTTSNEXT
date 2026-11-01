package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.Color
import com.kigtts.next.media.color.PhotoEffects
import com.kigtts.next.media.color.applyPhotoEffects
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoEffectsBitmapTest {
    @Test fun stackedEffectsWithTheReportedBloomAmountStayInsideTheirSourceBands() = runBlocking {
        val bitmap = Bitmap.createBitmap(96, 1537, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(170, 140, 90))
            applyPhotoEffects(bitmap, PhotoEffects(vignette = .6f, grain = .6f, aberration = .6f, bloom = .7975146f))
            for (y in listOf(0, 31, 32, 63, 64, 1503, 1504, 1536)) {
                assertEquals(255, Color.alpha(bitmap.getPixel(48, y)))
            }
        } finally { bitmap.recycle() }
    }

    @Test fun weakBloomPreservesItsSamplingReachAcrossBandBoundaries() = runBlocking {
        for ((width, height) in listOf(73 to 701, 701 to 73, 1 to 129, 129 to 1)) {
            val original = IntArray(width * height) { index ->
                val level = if ((index / width) % 13 < 6) 248 else 80
                Color.rgb(level, (level * .8f).roundToInt(), (level * .5f).roundToInt())
            }
            val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val weak = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                full.setPixels(original, 0, width, 0, 0, width, height)
                applyPhotoEffects(full, PhotoEffects(bloom = 1f))
                val bright = IntArray(original.size).also { full.getPixels(it, 0, width, 0, 0, width, height) }
                for (strength in listOf(.01f, .1f, .35f)) {
                    weak.setPixels(original, 0, width, 0, 0, width, height)
                    applyPhotoEffects(weak, PhotoEffects(bloom = strength))
                    val actual = IntArray(original.size).also { weak.getPixels(it, 0, width, 0, 0, width, height) }
                    for (index in original.indices) {
                        assertEquals(255, Color.alpha(actual[index]))
                        for (shift in listOf(0, 8, 16)) {
                            val before = original[index] ushr shift and 255
                            val after = bright[index] ushr shift and 255
                            val expected = before + (after - before) * strength
                            val value = actual[index] ushr shift and 255
                            assertTrue("$width x $height: pixel $index, bloom $strength", abs(value - expected) <= 1.1f)
                        }
                    }
                }
            } finally { full.recycle(); weak.recycle() }
        }
    }
}
