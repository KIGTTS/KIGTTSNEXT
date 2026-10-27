package com.kigtts.next.identity

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.identity.decodeProfileImage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileImageDecoderTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test @Config(sdk = [28, 35]) fun gifCanRenderAndPauseOnBothImageDecoderApis() {
        val file = File(app.cacheDir, "profile-animated.gif")
        file.writeBytes(javaClass.classLoader!!.getResourceAsStream("fixtures/profile/animated.gif")!!.use { it.readBytes() })
        val drawable = decodeProfileImage(app.resources, file)!!
        assertTrue(drawable is Animatable)
        drawable.setBounds(0, 0, 80, 48)
        val frame = Bitmap.createBitmap(80, 48, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(frame))
        assertTrue((0 until frame.width).any { frame.getPixel(it, 24) != 0 })
        (drawable as Animatable).start()
        assertTrue(drawable.isRunning)
        drawable.stop()
        assertFalse(drawable.isRunning)
        frame.recycle()
    }

    @Test fun staticDecodeIsBoundedAndMalformedFilesDoNotCrash() {
        val file = File(app.cacheDir, "profile-large.png")
        val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val drawable = decodeProfileImage(app.resources, file) as BitmapDrawable
        assertTrue(drawable.bitmap.width in 1..1200)
        assertTrue(drawable.bitmap.height in 1..1200)
        val invalid = File(app.cacheDir, "profile-invalid-image").apply { writeText("invalid image") }
        assertNull(decodeProfileImage(app.resources, invalid))
    }
}
