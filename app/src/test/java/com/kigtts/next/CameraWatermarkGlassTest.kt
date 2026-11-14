package com.kigtts.next

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraWatermarkGlassTest {
    @Test fun classicDefaultsToGlassButSavedSolidChoicesKeepTheirOriginalMeaning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val classic = CameraWatermarkLibrary(context).choices().single { it.id == "classic" }.defaults
        assertEquals(WatermarkPalette.GLASS, classic.palette)
        assertEquals(WatermarkPosition.BOTTOM, classic.position)
        assertEquals(classic, CameraWatermarkStyle.decode(classic.encode()))
        val solid = classic.copy(palette = WatermarkPalette.LIGHT)
        assertEquals(solid, CameraWatermarkStyle.decode(solid.encode()))
        val thirdParty = CameraWatermarkAsset.parse(
            "<watermark version='1' id='sample' name='自定义' layout='STANDARD' palette='GLASS' position='TOP' edge='5'/>")
        assertEquals(WatermarkPalette.GLASS, thirdParty.defaults.palette)
    }

    @Test fun glassSamplesTheSelectedPhotoEdgeAndNeverPaintsThePhoto() {
        val image = Bitmap.createBitmap(184, 144, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(image)
            val paper = RectF(0f, 0f, 184f, 144f)
            val photo = RectF(12f, 22f, 172f, 122f)
            val source = Rect(12, 22, 172, 122)
            val paint = Paint()
            for (position in WatermarkPosition.entries) {
                canvas.drawColor(Color.WHITE)
                paint.color = Color.RED
                canvas.drawRect(photo.left, photo.top, photo.right, photo.centerY(), paint)
                paint.color = Color.BLUE
                canvas.drawRect(photo.left, photo.centerY(), photo.right, photo.bottom, paint)
                val x = photo.centerX().toInt()
                val samples = listOf(23, 65, 73, 121)
                val before = samples.map { image.getPixel(x, it) }
                PhotoFrameGlass.prepare(image, source, position, 14, sideEdges = true).use { it.draw(canvas, paper, photo) }
                val edge = image.getPixel(x, if (position == WatermarkPosition.TOP) 10 else 132)
                val color = if (position == WatermarkPosition.TOP) Color.red(edge) else Color.blue(edge)
                val other = if (position == WatermarkPosition.TOP) Color.blue(edge) else Color.red(edge)
                assertTrue(color in 170..225)
                assertTrue(other < 10)
                assertEquals(before, samples.map { image.getPixel(x, it) })
                assertNotEquals(Color.WHITE, image.getPixel(6, 50))
                assertNotEquals(Color.WHITE, image.getPixel(178, 95))
            }
        } finally { image.recycle() }
    }
}
