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
    @Test fun exportContainsFiveConnectedContactsAndAWhiteHorizontalOverflow() {
        val f = CardPrintCoordinatorTest.Fixture()
        try {
            val person = IdentityCard(designId = "test", contacts = List(8) { ContactLink("QQ", "account$it") })
            val pixels = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
            drawIdentityContacts(Canvas(pixels), person, f.stickers, 720f)
            val size = (720 * .085f).toInt(); val button = size * 40f / 22f; val gap = button * .05f
            val sixthLeft = 720 * .04f + 5 * (button + gap)
            val y = (720 * .04f + button / 2).toInt()
            assertEquals(Color.WHITE, pixels.getPixel((sixthLeft + button / 2f).toInt(), y))
            val backing = pixels.getPixel((sixthLeft + 4).toInt(), y)
            assertTrue("暗底应保持半透明", Color.alpha(backing) in 1..200)
            assertEquals(0, Color.red(backing))
            assertEquals("不能绘制第7个图标", 0, Color.alpha(pixels.getPixel((sixthLeft + button + gap + button / 2).toInt(), y)))
            assertEquals("按钮间保留分隔", 0, Color.alpha(pixels.getPixel((sixthLeft - gap / 2).toInt(), y)))
            pixels.recycle()
        } finally { f.scope.cancel() }
    }
}
