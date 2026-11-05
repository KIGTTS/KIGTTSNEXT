package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.kigtts.next.cards.PhotoCrop
import com.kigtts.next.cards.PhotoFrameSpec
import com.kigtts.next.cards.PolaroidPhotoRenderer
import com.kigtts.plugin.api.PolaroidCard
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoCropRenderTest {
    @Test fun offCentreCropSurvivesAFrameChangeAndDoesNotRewriteTheSource() {
        val source = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        val result = Bitmap.createBitmap(50, 40, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.RED)
            Canvas(source).drawRect(50f, 40f, 100f, 80f, Paint().apply { color = Color.BLUE })
            val frame = PhotoFrameSpec(50, 40, "none", PhotoCrop(.5f, .5f, 1f, 1f))
            val restyled = requireNotNull(PhotoFrameSpec.from(frame.id)).copy(style = "classic")
            val card = PolaroidCard(title = "", frameStyleId = restyled.id)
            PolaroidPhotoRenderer.draw(Canvas(result), source, RectF(0f, 0f, 50f, 40f), Paint(), card)
            assertEquals(Color.BLUE, result.getPixel(0, 0))
            assertEquals(Color.BLUE, result.getPixel(49, 39))
            assertEquals(Color.RED, source.getPixel(0, 0))
            assertNull(PhotoFrameSpec.from("photo.100x80.classic")?.crop)
        } finally {
            source.recycle()
            result.recycle()
        }
    }
}
