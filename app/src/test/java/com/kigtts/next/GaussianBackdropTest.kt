package com.kigtts.next

import android.graphics.Bitmap
import android.graphics.Color
import com.kigtts.next.media.GaussianBackdrop
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26])
class GaussianBackdropTest {
    @Test fun gaussianSoftensSharpBoundaryWithoutIntroducingBlackEdgesAndKeepsSource() {
        val source = Bitmap.createBitmap(96,48,Bitmap.Config.ARGB_8888)
        for (y in 0 until 48) for (x in 0 until 96) source.setPixel(x,y, if (x<48) Color.BLUE else Color.WHITE)
        val result = GaussianBackdrop.blur(source)
        val boundary = result.getPixel(47,24)
        assertTrue(Color.red(boundary) in 40..200); assertEquals(255,Color.blue(boundary)); assertEquals(255,Color.alpha(boundary))
        assertEquals(Color.BLUE,result.getPixel(0,24)); assertEquals(Color.WHITE,result.getPixel(95,24))
        assertFalse(source.isRecycled); assertEquals(Color.BLUE,source.getPixel(47,24))
        result.recycle(); source.recycle()
    }
    @Test fun largeImagesAreBoundedAndSolidColorSurvivesApi26Blur() {
        val source = Bitmap.createBitmap(1024,512,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        val result = GaussianBackdrop.blur(source)
        assertEquals(128,result.width); assertEquals(64,result.height); assertEquals(Color.CYAN,result.getPixel(0,0)); assertFalse(source.isRecycled)
        result.recycle(); source.recycle()
    }
}
