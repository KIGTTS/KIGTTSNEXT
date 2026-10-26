package com.kigtts.next.identity

import android.graphics.*
import com.kigtts.next.CardPrintCoordinatorTest
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class IdentityContactArtworkTest {
    @Test fun exportContainsFiveContactsAndAWhiteHorizontalOverflowWithFeatheredDarkBacking() {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val person = IdentityCard(designId = "test", contacts = List(8) { ContactLink("QQ", "account$it") })
            val pixels = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
            drawIdentityContacts(Canvas(pixels), person, f.stickers, 720f)
            val size = (720 * .085f).toInt(); val top = 720 * .06f
            val sixthLeft = 720 * .06f + 5 * 720 * .15f
            val y = (top + size / 2f).toInt()
            assertEquals(Color.WHITE, pixels.getPixel((sixthLeft + size / 2f).toInt(), y))
            val feather = pixels.getPixel((sixthLeft - 8).toInt(), y)
            assertTrue("暗底应保持半透明", Color.alpha(feather) in 1..200)
            assertEquals(0, Color.red(feather))
            assertEquals("不能绘制第7个图标", 0, Color.alpha(pixels.getPixel((sixthLeft + 720 * .15f + size / 2f).toInt(), y)))
            pixels.recycle()
        } finally { f.scope.cancel() }
    }
}
