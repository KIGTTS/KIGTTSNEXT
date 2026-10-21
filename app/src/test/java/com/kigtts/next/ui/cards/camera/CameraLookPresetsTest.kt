package com.kigtts.next.ui.cards.camera

import com.kigtts.next.media.color.CubeLut
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CameraLookPresetsTest {
    @get:Rule val temp = TemporaryFolder()
    private val colors = listOf(0x7f246ac3, 0xffd1a546.toInt(), 0xff517c55.toInt(), 0xffbe8872.toInt(), 0xff888888.toInt())
    @Test fun newLooksAreDistinctPreserveAlphaAndStrengthAndRoundTripTheMotionDescriptor() {
        val results = mutableSetOf<List<Int>>()
        for (filter in CameraLookPresets.filters) {
            val lut = requireNotNull(PhotoFilters.lut(filter))
            assertSame(lut, PhotoFilters.lut(filter)); assertEquals(33, lut.size)
            val file = temp.newFile(); lut.writeBinary(file); val restored = CubeLut.readBinary(file)
            results += colors.map { color ->
                val full = lut.map(color, 1f)
                assertEquals(color, lut.map(color, 0f))
                assertEquals(color and -0x1000000, full and -0x1000000)
                assertEquals(full, restored.map(color, 1f))
                for (shift in listOf(0, 8, 16)) assertEquals(((color ushr shift and 255) + (full ushr shift and 255)) / 2f,
                    (lut.map(color, .5f) ushr shift and 255).toFloat(), 1f)
                full
            }
        }
        assertEquals(5, results.size)
        assertTrue(PhotoFilter.entries.map { it.label }.containsAll(listOf("原画", "黑白", "暖色", "冷色", "复古", "柔彩胶片", "鲜明胶片", "淡彩胶片", "黑金", "写意", "季风", "京都", "旧巷")))
    }
    @Test fun blackGoldSuppressesCoolColorsAndRetainsGoldWhileInkDesaturates() {
        val gold = requireNotNull(PhotoFilters.lut(PhotoFilter.BLACK_GOLD))
        val warm = gold.map(0xffd1a546.toInt(), 1f)
        val blue = gold.map(0xff4080c0.toInt(), 1f)
        assertTrue((warm ushr 16 and 255) > (warm and 255) + 45)
        assertTrue((blue ushr 16 and 255) < 100)
        val ink = requireNotNull(PhotoFilters.lut(PhotoFilter.INK)).map(0xff4080c0.toInt(), 1f)
        val channels = listOf(ink ushr 16 and 255, ink ushr 8 and 255, ink and 255)
        assertTrue(channels.max() - channels.min() < 30)
        assertNull(PhotoFilters.lut(PhotoFilter.ORIGINAL)); assertNull(PhotoFilters.lut(PhotoFilter.WARM))
    }
}
