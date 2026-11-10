package com.kigtts.next.cards

import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PhotoFrameOrientationTest {
    @Test fun nativeDimensionsAndExifRotationDoNotSwapTheFrameTwice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("camera-frame", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            val variants = mapOf(
                ExifInterface.ORIENTATION_NORMAL to (160 to 90),
                ExifInterface.ORIENTATION_ROTATE_180 to (160 to 90),
                ExifInterface.ORIENTATION_ROTATE_90 to (90 to 160),
                ExifInterface.ORIENTATION_ROTATE_270 to (90 to 160),
                ExifInterface.ORIENTATION_TRANSVERSE to (90 to 160),
                ExifInterface.ORIENTATION_TRANSPOSE to (90 to 160))
            variants.forEach { (orientation, expected) ->
                ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
                val original = file.readBytes()
                val (width, height) = stickerDisplayDimensions(file, 160, 90)
                assertEquals(expected, width to height)
                val frame = PhotoFrameSpec(width, height, "classic").geometry()
                assertEquals(width.toDouble() / height, (frame.photoRight - frame.photoLeft) / (frame.photoBottom - frame.photoTop), .0001)
                assertTrue(frame.paperHeight > frame.photoBottom && frame.paperWidth > frame.photoRight)
                assertArrayEquals(original, file.readBytes())
            }
        } finally { bitmap.recycle(); file.delete() }
    }
}
