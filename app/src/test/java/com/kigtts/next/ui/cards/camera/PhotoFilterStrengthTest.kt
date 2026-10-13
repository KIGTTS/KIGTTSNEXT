package com.kigtts.next.ui.cards.camera

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.test.runTest
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
class PhotoFilterStrengthTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun zeroIsIdentityFullIsThePresetAndIntermediateStrengthPreservesAlpha() {
        val identity = PhotoFilters.coefficients(PhotoFilter.ORIGINAL)
        for (filter in PhotoFilter.entries) {
            val preset = PhotoFilters.coefficients(filter)
            assertArrayEquals(identity, PhotoFilters.coefficients(filter, 0f), .00001f)
            assertArrayEquals(preset, PhotoFilters.coefficients(filter, 1f), .00001f)
            val middle = PhotoFilters.coefficients(filter, .5f)
            for (i in middle.indices) assertEquals((identity[i] + preset[i]) / 2f, middle[i], .00001f)
            assertArrayEquals(identity.copyOfRange(15, 20), middle.copyOfRange(15, 20), .00001f)
        }
    }

    @Test fun zeroStrengthPreservesTheOriginalJpegExactlyAndWritesNoFilteredFile() = runTest {
        val source = directory.newFile("original.jpg"); val output = java.io.File(directory.root, "processed.jpg")
        val bitmap = Bitmap.createBitmap(23, 31, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(70, 140, 210)) }
        try { source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 98, it)) } }
        finally { bitmap.recycle() }
        ExifInterface(source).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes() }
        val originalBytes = source.readBytes()
        assertNull(PhotoFilterWriter.write(source, output, PhotoFilter.WARM, 0f))
        assertFalse(output.exists()); assertArrayEquals(originalBytes, source.readBytes())
        assertEquals("6", source.inputStream().use { ExifInterface(it).getAttribute(ExifInterface.TAG_ORIENTATION) })
    }
}
