package com.kigtts.next

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.*
import com.kigtts.next.ui.components.TextMenuPosition
import org.junit.Assert.*
import org.junit.Test

class TextMenuPositionTest {
    private val window = IntSize(400, 800)
    private val popup = IntSize(180, 92)
    private val anchor = IntRect(0, 0, 400, 800)

    @Test fun shadowPaddingKeepsTheVisibleSurfaceAtTheOriginalAboveAndBelowSelectionGap() {
        var selected = Rect(90f, 300f, 130f, 320f)
        val position = TextMenuPosition({ selected }, 24, 300, 8, shadowInset = 18)
        val above = position.calculatePosition(anchor, window, LayoutDirection.Ltr, popup)
        assertEquals(selected.top.toInt() - 8, above.y + popup.height - 18)
        selected = Rect(90f, 60f, 130f, 90f)
        val below = position.calculatePosition(anchor, window, LayoutDirection.Ltr, popup)
        assertEquals(selected.bottom.toInt() + 8, below.y + 18)
    }

    @Test fun theCompleteShadowWindowFitsSystemAndKeyboardInsetsIncludingRtl() {
        val position = TextMenuPosition({ Rect(380f, 730f, 400f, 760f) }, 24, 300, 8,
            shadowInset = 18, leftInset = 12, rightInset = 22)
        for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            val offset = position.calculatePosition(anchor, window, direction, popup)
            assertTrue(offset.x >= 20 && offset.x + popup.width <= 370)
            assertTrue(offset.y >= 32 && offset.y + popup.height <= 492)
        }
    }

    @Test fun titleAndBodyPlacementUseTheCurrentFieldAndNativeWindowOffsetRatherThanCachedSelection() {
        var selected = Rect(20f, 80f, 220f, 120f)
        val nativeAnchor = IntRect(10, 50, 410, 850)
        val position = TextMenuPosition({ selected }, 24, 0, 8, shadowInset = 18)
        val body = position.calculatePosition(nativeAnchor, IntSize(450, 900), LayoutDirection.Ltr, popup)
        assertEquals(50 + 80 - 8, body.y + popup.height - 18)
        selected = Rect(20f, 420f, 220f, 450f)
        val title = position.calculatePosition(nativeAnchor, IntSize(450, 900), LayoutDirection.Ltr, popup)
        assertEquals(50 + 420 - 8, title.y + popup.height - 18)
        assertTrue(title.y > body.y)
    }

    @Test fun theResizedNativeViewportKeepsItsOriginAndDoesNotSubtractTheKeyboardTwice() {
        val nativeFrame = IntRect(28, 54, 400, 520)
        val position = TextMenuPosition({ Rect(375f, 458f, 400f, 480f) }, 54, 280, 8,
            visibleWindow = { nativeFrame })
        val offset = position.calculatePosition(anchor, IntSize(372, 466), LayoutDirection.Ltr, popup)
        assertEquals(212, offset.x)
        assertEquals(358, offset.y)
    }

    @Test fun tallSelectionsCannotPutTheMenuOverTheSubtitlesFontControls() {
        val editor = IntRect(0, 188, 400, 490)
        val position = TextMenuPosition({ Rect(32f, 8f, 380f, 292f) }, 24, 0, 8, shadowInset = 18,
            keepInContent = true)
        val offset = position.calculatePosition(editor, window, LayoutDirection.Ltr, popup)
        assertTrue(offset.y >= editor.top + 8)
        assertTrue(offset.y + popup.height <= editor.bottom - 8)
    }
}
