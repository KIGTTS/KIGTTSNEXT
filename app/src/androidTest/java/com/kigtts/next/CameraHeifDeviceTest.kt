package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kigtts.next.media.CameraPhotoEncoding
import com.kigtts.next.media.encodeCameraPhoto
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class CameraHeifDeviceTest {
    @Test fun encodesAndDecodesHeif() {
        assumeTrue("No HEIF-compatible encoder", CameraPhotoEncoding.heifAvailable)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("heif-check-", ".heic", context.cacheDir)
        val source = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.RED)
            encodeCameraPhoto(source, file, CameraPhotoEncoding.HEIF)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            assertEquals("HEIF width for ${file.length()} bytes (${bounds.outMimeType})", 1200, bounds.outWidth)
            assertEquals(1600, bounds.outHeight)
            assertTrue(bounds.outMimeType in listOf("image/heic", "image/heif"))
            val decoded = requireNotNull(BitmapFactory.decodeFile(file.path))
            try { assertTrue(Color.red(decoded.getPixel(600, 800)) > 200) }
            finally { decoded.recycle() }
        } finally { source.recycle(); file.delete() }
    }
}
