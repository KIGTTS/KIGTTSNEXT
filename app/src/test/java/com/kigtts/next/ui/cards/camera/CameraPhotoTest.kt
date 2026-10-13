package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CameraPhotoTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun phoneRectangleFollowsOrientationWhileTabletAndSquareWindowHaveThreeChoices() {
        assertEquals(listOf(PolaroidFormat.MINI, PolaroidFormat.SQUARE), cameraFormats(393, 852, 393))
        assertEquals(listOf(PolaroidFormat.WIDE, PolaroidFormat.SQUARE), cameraFormats(852, 393, 393))
        assertEquals(listOf(PolaroidFormat.MINI, PolaroidFormat.WIDE, PolaroidFormat.SQUARE), cameraFormats(800, 1280, 800))
        assertEquals(3, cameraFormats(500, 550, 500).size)
        assertEquals("矩形", PolaroidFormat.MINI.cameraLabel(false)); assertEquals("横版", PolaroidFormat.WIDE.cameraLabel(true))
    }
    @Test fun liveMatrixAndSavedColorCoefficientsAreSharedWithoutChangingAlpha() {
        val source = Color.argb(125, 80, 110, 140)
        for (filter in PhotoFilter.entries) {
            val coefficients = PhotoFilters.coefficients(filter)
            assertArrayEquals(coefficients, PhotoFilters.matrix(filter).array, .0001f)
            assertEquals(125, Color.alpha(PhotoFilters.apply(source, coefficients)))
        }
        val gray = PhotoFilters.apply(source, PhotoFilters.coefficients(PhotoFilter.BW))
        assertEquals(Color.red(gray), Color.green(gray)); assertEquals(Color.red(gray), Color.blue(gray))
        assertEquals(source, PhotoFilters.apply(source, PhotoFilters.coefficients(PhotoFilter.ORIGINAL)))
    }
    @Test fun highResolutionFilterPreservesOriginalBytesDimensionsAndExifOrientation() = runTest {
        val original = jpeg(2100, 64)
        ExifInterface(original).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val bytes = original.readBytes(); val output = File(temporary.root, "filtered.jpg")
        assertEquals(output, PhotoFilterWriter.write(original, output, PhotoFilter.BW))
        assertArrayEquals(bytes, original.readBytes())
        val decoded = output.inputStream().use { BitmapFactory.decodeStream(it) }!!
        try {
            assertEquals(2100, decoded.width); assertEquals(64, decoded.height)
            val color = decoded.getPixel(1000, 30)
            assertTrue(kotlin.math.abs(Color.red(color) - Color.green(color)) <= 2)
            assertTrue(kotlin.math.abs(Color.red(color) - Color.blue(color)) <= 2)
        } finally { decoded.recycle() }
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90,
            output.inputStream().use { ExifInterface(it) }.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        assertTrue(original.delete()); assertTrue(output.delete())
    }
    @Test fun originalFilterNeverCreatesAReencodedFile() = runTest {
        val original = jpeg(64, 32); val bytes = original.readBytes(); val output = File(temporary.root, "unused.jpg")
        assertNull(PhotoFilterWriter.write(original, output, PhotoFilter.ORIGINAL))
        assertArrayEquals(bytes, original.readBytes()); assertFalse(output.exists())
    }
    @Test fun captureOwnershipDeletesBothTemporaryCopiesAndDoesNotDeleteOtherFiles() {
        val original = temporary.newFile("original.jpg"); val processed = temporary.newFile("processed.jpg")
        val unrelated = temporary.newFile("other.jpg")
        val capture = PolaroidCameraCapture(original, processed, PolaroidFormat.WIDE, PhotoFilter.WARM)
        assertEquals(processed, capture.displayFile)
        capture.discard(); capture.close()
        assertFalse(original.exists()); assertFalse(processed.exists()); assertTrue(unrelated.exists())
    }
    private fun jpeg(width: Int, height: Int): File {
        val original = File(temporary.root, "original.jpg")
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try { image.eraseColor(Color.rgb(150, 90, 30)); original.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.JPEG, 98, it)) } }
        finally { image.recycle() }
        return original
    }
}
