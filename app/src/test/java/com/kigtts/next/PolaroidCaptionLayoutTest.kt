package com.kigtts.next

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.app.Application
import android.text.Spanned
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.next.cards.*
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PolaroidCaptionLayoutTest {
    @Test fun captureFieldsFlowBelowNoteLeftAlignedWithinEveryPaperFormat() {
        MaterialSymbols.initialize(ApplicationProvider.getApplicationContext<Application>())
        val card = PolaroidCard(title = "标题", description = "备注内容".repeat(80), layers = listOf(
            PhotoTextField.DATE to "2026.10.22", PhotoTextField.TIME to "12:30", PhotoTextField.LOCATION to "拍摄地点", PhotoTextField.WEATHER to "晴 24°C"
        ).map { (field, value) -> photoInformationLayer(field, value) })
        PolaroidFormat.entries.forEach { format ->
            val geometry = format.geometry
            val width = 540f
            val caption = polaroidCaption(card, geometry, geometry.titleWidth * .7, width, Color.BLACK, Color.DKGRAY)
            assertEquals(3, caption.lines.size)
            assertTrue(caption.description.indexOf("备注") < caption.description.indexOf("2026"))
            caption.lines.forEach { assertEquals((geometry.titleX * width / geometry.paperWidth).toFloat(), it.x, .01f) }
            caption.lines.zipWithNext().forEach { (above, below) -> assertTrue(above.y + above.layout.height <= below.y) }
            val last = caption.lines.last()
            assertEquals("Metadata is one row on $format", 1, last.layout.lineCount)
            val text = last.layout.text as Spanned
            assertEquals(4, text.getSpans(0, text.length, PhotoInformationIconSpan::class.java).size)
            assertTrue("Caption leaves paper for $format", last.y + last.layout.height <= geometry.paperHeight * width / geometry.paperWidth)
            val image = Bitmap.createBitmap(width.toInt(), (geometry.paperHeight * width / geometry.paperWidth).toInt(), Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(image); canvas.drawColor(Color.WHITE)
                val scale = width / geometry.paperWidth.toFloat()
                canvas.drawRect((geometry.photoLeft * scale).toFloat(), (geometry.photoTop * scale).toFloat(),
                    (geometry.photoRight * scale).toFloat(), (geometry.photoBottom * scale).toFloat(), Paint().apply { color = Color.LTGRAY })
                caption.draw(canvas)
                val output = File("build/previews/capture-information-${format.name}.png").apply { requireNotNull(parentFile).mkdirs() }
                output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { image.recycle() }
        }
    }
}
