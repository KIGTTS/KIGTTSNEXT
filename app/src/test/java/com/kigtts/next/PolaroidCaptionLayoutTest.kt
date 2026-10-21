package com.kigtts.next

import android.graphics.Color
import com.kigtts.next.cards.*
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PolaroidCaptionLayoutTest {
    @Test fun captureFieldsFlowBelowNoteLeftAlignedWithinEveryPaperFormat() {
        val card = PolaroidCard(title = "标题", description = "备注内容".repeat(80), layers = listOf(
            "2026.10.22", "12:30", "拍摄地点", "晴 24°C"
        ).map { CardLayer(kind = CardLayerKind.PHOTO_INFO, text = it) })
        PolaroidFormat.entries.forEach { format ->
            val geometry = format.geometry
            val width = 540f
            val caption = polaroidCaption(card, geometry, geometry.titleWidth * .7, width, Color.BLACK, Color.DKGRAY)
            assertEquals(6, caption.lines.size)
            assertTrue(caption.description.indexOf("备注") < caption.description.indexOf("2026"))
            caption.lines.forEach { assertEquals((geometry.titleX * width / geometry.paperWidth).toFloat(), it.x, .01f) }
            caption.lines.zipWithNext().forEach { (above, below) -> assertTrue(above.y + above.layout.height <= below.y) }
            val last = caption.lines.last()
            assertTrue("Caption leaves paper for $format", last.y + last.layout.height <= geometry.paperHeight * width / geometry.paperWidth)
        }
    }
}
