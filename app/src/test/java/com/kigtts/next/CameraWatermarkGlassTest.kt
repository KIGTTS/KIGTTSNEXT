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
    @Test fun builtinWatermarksUseNewStylesAndOldSnapshotsStillDecode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val styles = CameraWatermarkLibrary(context).choices().associate { it.id to it.defaults }
        val classic = styles.getValue("classic")
        assertEquals(WatermarkPalette.WHITE, classic.palette)
        assertEquals(WatermarkGlassSource.FULL, classic.glassSource)
        assertEquals(WatermarkPosition.BOTTOM, classic.position)
        assertEquals(classic, CameraWatermarkStyle.decode(classic.encode()))
        val custom = classic.copy(palette = WatermarkPalette.CUSTOM, customColor = 0xff387e88.toInt())
        assertEquals(custom, PhotoFrameSpec.from(PhotoFrameSpec(3000, 4000, custom.encode()).id)?.watermark)
        val standard = styles.getValue("standard")
        assertEquals(WatermarkPalette.GLASS, standard.palette)
        assertTrue(standard.lockEdge)
        assertEquals(standard, CameraWatermarkStyle.decode(standard.encode()))
        val overlay = styles.getValue("check-in")
        assertTrue(overlay.showLogo)
        assertTrue(overlay.overlay)
        val geometry = PhotoFrameSpec(3000, 4000, overlay.encode()).geometry()
        assertEquals(geometry.photoHeight, geometry.paperHeight, .001)
        assertEquals(geometry.photoWidth, geometry.paperWidth, .001)
        assertEquals(WatermarkPalette.LIGHT, CameraWatermarkStyle.decode("wm1-2-0-0-2s-3-e-c")?.palette)
        val thirdParty = CameraWatermarkAsset.parse(
            "<watermark version='1' id='sample' name='自定义' layout='STANDARD' palette='GLASS' position='TOP' edge='5'/>")
        assertEquals(WatermarkPalette.GLASS, thirdParty.defaults.palette)
    }

    @Test fun classicFullPhotoUsesOneCoverImageAndKeepsThePhotoSharp() {
        val image = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(image)
            canvas.drawColor(Color.BLACK)
            val photo = RectF(24f, 32f, 276f, 168f)
            val paint = Paint()
            paint.color = Color.RED
            canvas.drawRect(photo.left, photo.top, photo.right, photo.centerY(), paint)
            paint.color = Color.BLUE
            canvas.drawRect(photo.left, photo.centerY(), photo.right, photo.bottom, paint)
            PhotoFrameGlass.prepare(image, Rect(24, 32, 276, 168), WatermarkPosition.BOTTOM, 14, true,
                WatermarkGlassSource.FULL, wholeBackdrop = true).use {
                it.draw(canvas, RectF(0f, 0f, 300f, 200f), photo)
            }
            assertTrue(Color.red(image.getPixel(150, 15)) > 150)
            assertTrue(Color.blue(image.getPixel(150, 185)) > 150)
            assertTrue(Color.red(image.getPixel(12, 55)) > 150)
            assertTrue(Color.blue(image.getPixel(12, 145)) > 150)
            assertEquals(Color.RED, image.getPixel(150, 45))
            assertEquals(Color.BLUE, image.getPixel(150, 150))
        } finally { image.recycle() }
    }

    @Test fun explicitSourcesRoundTripAndRemainIndependentOfPlacement() {
        val xml = "<watermark version='1' id='sample' name='预览' layout='CLASSIC' palette='GLASS' position='BOTTOM'"
        for (source in WatermarkGlassSource.entries) {
            val style = CameraWatermarkAsset.parse("$xml glassSource='${source.name}'/>").defaults
            assertEquals(WatermarkPosition.BOTTOM, style.position)
            assertEquals(source, style.glassSource)
            assertEquals(style, CameraWatermarkStyle.decode(style.encode()))
        }
        assertEquals(WatermarkGlassSource.AUTO, CameraWatermarkAsset.parse("$xml/>").defaults.glassSource)
        assertThrows(IllegalArgumentException::class.java) { CameraWatermarkAsset.parse("$xml glassSource='UNKNOWN'/>") }
    }

    @Test fun allFourEdgesAndWholePhotoCanFeedTheSameBottomBand() {
        val bitmap = Bitmap.createBitmap(260, 200, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paper = RectF(0f, 0f, 260f, 200f)
            val photo = RectF(10f, 16f, 250f, 152f)
            val photoPixels = Rect(10, 16, 250, 152)
            val paint = Paint()
            fun fill(color: Int, left: Float, top: Float, right: Float, bottom: Float) {
                paint.color = color
                canvas.drawRect(left, top, right, bottom, paint)
            }
            fun render(source: WatermarkGlassSource): Int {
                canvas.drawColor(Color.WHITE)
                fill(Color.GRAY, photo.left, photo.top, photo.right, photo.bottom)
                fill(Color.RED, 10f, 16f, 250f, 47f)
                fill(Color.BLUE, 10f, 121f, 250f, 152f)
                fill(Color.GREEN, 10f, 16f, 41f, 152f)
                fill(Color.YELLOW, 219f, 16f, 250f, 152f)
                PhotoFrameGlass.prepare(bitmap, photoPixels, WatermarkPosition.BOTTOM, 14,
                    sideEdges = false, sampling = source).use { it.draw(canvas, paper, photo) }
                assertEquals(Color.WHITE, bitmap.getPixel(130, 8))
                assertEquals(Color.GRAY, bitmap.getPixel(130, 84))
                return bitmap.getPixel(130, 176)
            }
            val top = render(WatermarkGlassSource.TOP)
            val bottom = render(WatermarkGlassSource.BOTTOM)
            val left = render(WatermarkGlassSource.LEFT)
            val right = render(WatermarkGlassSource.RIGHT)
            val full = render(WatermarkGlassSource.FULL)
            assertTrue(Color.red(top) > 165 && Color.blue(top) < 24)
            assertTrue(Color.blue(bottom) > 165 && Color.red(bottom) < 24)
            assertTrue(Color.green(left) > 165 && Color.red(left) < 24)
            assertTrue(Color.red(right) > 165 && Color.green(right) > 165)
            assertTrue(Color.red(full) in 65..140 && Color.blue(full) in 65..140)
        } finally { bitmap.recycle() }
    }

    @Test fun standardWatermarkLogoGetsMoreSpaceWithoutEnlargingCenteredOrClassicLogos() {
        assertEquals(27f, watermarkLogoHeight(WatermarkLayout.STANDARD, false, 1000f, 90f, 4f), .01f)
        assertEquals(19.8f, watermarkLogoHeight(WatermarkLayout.STANDARD, true, 1000f, 90f, 4f), .01f)
        assertEquals(19.8f, watermarkLogoHeight(WatermarkLayout.CLASSIC, false, 1000f, 90f, 4f), .01f)
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
