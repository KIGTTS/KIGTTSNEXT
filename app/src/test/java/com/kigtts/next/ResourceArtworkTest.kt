package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.Color
import com.kigtts.next.media.decodeResourceArtwork
import com.kigtts.next.media.isWhiteLineArtwork
import com.kigtts.next.media.decodeResourceVector
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ResourceArtworkTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun hashNamedSvgRendersAndUnsafeVectorFallsBack() {
        val file = temp.newFile("hash.img")
        file.writeText("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><rect width=\"24\" height=\"24\" fill=\"#ff0000\"/></svg>")
        val result = requireNotNull(decodeResourceArtwork(file))
        assertEquals(256, result.width); assertEquals(Color.RED, result.getPixel(128, 128)); result.recycle()
        file.writeText("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>")
        assertNull(decodeResourceArtwork(file))
        assertNull(decodeResourceVector(file))
        file.writeText("<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/hosts'>]><svg xmlns=\"http://www.w3.org/2000/svg\">&x;</svg>")
        assertNull(decodeResourceArtwork(file))
    }
    @Test fun bitmapsRemainSupportedAndMalformedImagesDoNotCrash() {
        val file = temp.newFile("bitmap.img")
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.BLUE); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        val result = requireNotNull(decodeResourceArtwork(file))
        assertEquals(Color.BLUE, result.getPixel(30, 30)); result.recycle()
        file.writeText("not an image"); assertNull(decodeResourceArtwork(file))
    }
    @Test fun onlyTransparentWhiteArtworkReceivesThemeTint() {
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT); bitmap.setPixel(10, 10, Color.WHITE)
        assertTrue(bitmap.isWhiteLineArtwork())
        bitmap.setPixel(10, 10, Color.RED); assertFalse(bitmap.isWhiteLineArtwork())
        bitmap.eraseColor(Color.WHITE); assertFalse(bitmap.isWhiteLineArtwork())
        bitmap.recycle()
    }
    @Test fun officialSourceSvgCarriesItsOwnCenteredWhitespace() {
        val file = java.io.File("../distribution/assets/kpm-source.svg")
        val bitmap = requireNotNull(decodeResourceArtwork(file))
        try {
            var left = bitmap.width; var right = 0; var top = bitmap.height; var bottom = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 128) {
                    left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
                }
            }
            assertTrue(bitmap.isWhiteLineArtwork())
            assertEquals(bitmap.width * .62f, (right - left + 1).toFloat(), 3f)
            assertEquals(bitmap.height * .62f, (bottom - top + 1).toFloat(), 3f)
            assertEquals((bitmap.width - 1) / 2f, (left + right) / 2f, 2f)
            assertEquals((bitmap.height - 1) / 2f, (top + bottom) / 2f, 2f)
        } finally { bitmap.recycle() }
        val vector = requireNotNull(decodeResourceVector(file))
        assertTrue(vector.whiteLine)
        val large = Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888)
        try {
            android.graphics.Canvas(large).apply { scale(4f,4f); drawPicture(vector.picture) }
            assertTrue(large.isWhiteLineArtwork())
            assertEquals(0, Color.alpha(large.getPixel(0,0)))
        } finally { large.recycle() }
    }

    @Test fun actualFontOutlinesUseTheSharedThemeMask() {
        val file = temp.newFile("font.svg")
        file.writeBytes(javaClass.getResourceAsStream("/fixtures/appearance-font/icon.svg")!!.use { it.readBytes() })
        val bitmap = requireNotNull(decodeResourceArtwork(file))
        try {
            assertTrue("Font glyphs must have no baked plate or fixed foreground colour", bitmap.isWhiteLineArtwork())
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        } finally { bitmap.recycle() }
    }
}
