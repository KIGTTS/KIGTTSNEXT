package com.kigtts.next.ui.cards.camera

import com.kigtts.next.media.color.CubeLut
import android.graphics.BitmapFactory
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import kotlinx.coroutines.test.runTest
import java.io.StringReader

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])
class CameraLutTest {
    @get:Rule val temp = TemporaryFolder()
    private fun cube(invert: Boolean = false) = CubeLut.read(StringReader(buildString {
        append("TITLE \"验收\"\nLUT_3D_SIZE 2\n")
        for (b in 0..1) for (g in 0..1) for (r in 0..1) append("${if (invert) 1-r else r} ${if (invert) 1-g else g} ${if (invert) 1-b else b}\n")
    }))
    @Test fun cubeInterpolationPreservesIdentityAndAlphaAndBinaryRoundTrips() {
        val identity = cube(); val inverted = cube(true)
        for (color in listOf(0xff000000.toInt(), -1, 0x7f2579bd, 0xffd91385.toInt())) {
            assertEquals(color, identity.map(color, 1f))
            assertEquals(color, inverted.map(color, 0f))
            assertEquals(color and -0x1000000, inverted.map(color, 1f) and -0x1000000)
            assertEquals((color and -0x1000000) or ((color xor 0xffffff) and 0xffffff), inverted.map(color, 1f))
        }
        val file = temp.newFile(); inverted.writeBinary(file)
        assertEquals(inverted.map(0xff2579bd.toInt(), .4f), CubeLut.readBinary(file).map(0xff2579bd.toInt(), .4f))
        val atlas = inverted.atlas(); try { assertEquals(0xffffff, atlas.getPixel(0, 0) and 0xffffff); assertEquals(0, atlas.getPixel(3, 1) and 0xffffff) } finally { atlas.recycle() }
    }
    @Test fun malformedOrOversizedOrNonFiniteLutsAreRejected() {
        for (text in listOf("LUT_3D_SIZE 66", "LUT_3D_SIZE 2\n0 0 NaN", "LUT_1D_SIZE 16", "LUT_3D_SIZE 2\n0 0 0"))
            assertThrows(RuntimeException::class.java) { CubeLut.read(StringReader(text)) }
    }
    @Test fun wideZoomUsesTheFirstQuarterWithOneTimesSnapAndTelephotoKeepsFullRange() {
        val range = .6f..8f
        assertEquals(0f, cameraZoomPosition(.6f, range), .0001f)
        assertEquals(.25f, cameraZoomPosition(1f, range), .0001f)
        assertEquals(1f, cameraZoomPosition(8f, range), .0001f)
        assertEquals(1f, cameraZoomFromPosition(.26f, range), 0f)
        for (ratio in listOf(.6f, .75f, 1f, 2f, 4f, 8f)) assertEquals(ratio, cameraZoomFromPosition(cameraZoomPosition(ratio, range), range), .001f)
    }
    @Test fun savedLutKeepsOriginalHighResolutionBytesAndTransformsTheActualPixels() = runTest {
        val original = temp.newFile("original.jpg"); val output = temp.newFile("result.jpg").apply { delete() }
        val image = android.graphics.Bitmap.createBitmap(2400, 64, android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(0xff204060.toInt()); original.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 98, it) }; image.recycle()
        val before = original.readBytes()
        PhotoFilterWriter.write(original, output, PhotoFilter.CUSTOM, 1f, cube(true))
        assertArrayEquals(before, original.readBytes())
        val result = BitmapFactory.decodeFile(output.path)
        try { assertEquals(2400, result.width); val pixel = result.getPixel(1200, 32)
            assertEquals(223.0, (pixel ushr 16 and 255).toDouble(), 3.0); assertEquals(191.0, (pixel ushr 8 and 255).toDouble(), 3.0)
        } finally { result.recycle() }
    }
}
