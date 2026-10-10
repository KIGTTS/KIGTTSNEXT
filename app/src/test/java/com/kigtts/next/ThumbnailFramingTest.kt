package com.kigtts.next

import com.kigtts.next.media.*
import com.kigtts.next.media.smartcrop.CropImage
import com.kigtts.next.media.thumbnail.ThumbnailFraming
import org.junit.Assert.*
import org.junit.Test

class ThumbnailFramingTest {
    private val entry = MediaEntry("fixture", "素材", MediaKind.IMAGE, "fixture.png")
    @Test fun transparentStickerTrimsHiddenRgbAndKeepsEntireVisibleExpression() {
        val image = CropImage(200, 160, IntArray(200 * 160) { i ->
            if (i % 200 in 60..139 && i / 200 in 40..119) 0xff00aabc.toInt() else 0x00ff0000
        })
        val region = ThumbnailFraming.region(image, 120, 60, entry)
        assertTrue(region.fit)
        assertTrue(region.left < 60 && region.top < 40)
        assertTrue(region.left + region.width > 139 && region.top + region.height > 119)
        assertTrue(region.width < 110 && region.height < 110)
    }
    @Test fun dispersedPosterDetailsAndHtmlArePreservedRatherThanCut() {
        val image = CropImage(256, 256, IntArray(256 * 256) { i ->
            val x = i % 256; val y = i / 256
            if ((x in 8..42 || x in 212..246) && (y in 8..42 || y in 212..246))
                if ((x + y) % 4 < 2) 0xffffffff.toInt() else 0xff000000.toInt() else 0xff555555.toInt()
        })
        val region = ThumbnailFraming.region(image, 150, 48, entry)
        assertTrue(region.fit); assertEquals(256, region.width); assertEquals(256, region.height)
        assertTrue(ThumbnailFraming.region(image, 150, 48, entry.copy(kind = MediaKind.HTML)).fit)
    }
    @Test fun manualFocusHasBoundedCoordinatesAndDoesNotChangePresentationScale() {
        val image = CropImage(320, 160, IntArray(320 * 160) { 0xff303030.toInt() })
        for (focus in listOf(0.0, .2, .7, 1.0)) {
            val manual = entry.copy(thumbnailMode = ThumbnailMode.FOCUS, focusX = focus, focusY = focus, scale = MediaScale.FIT)
            val region = ThumbnailFraming.region(image, 80, 80, manual)
            assertFalse(region.fit); assertTrue(region.left >= 0 && region.left + region.width <= 320)
            assertTrue(region.top >= 0 && region.top + region.height <= 160)
            assertEquals(MediaScale.FIT, manual.scale)
        }
        assertTrue(ThumbnailFraming.region(image, 80, 80, entry.copy(thumbnailMode = ThumbnailMode.FIT)).fit)
    }
}
