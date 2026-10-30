package com.kigtts.next.overlay

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class OverlayMotionGeometryTest {
    private val safe = OverlayBounds(0, 90, 1080, 2200)
    @Test fun draggingReservesTopAndBottomMenuSpaceWithOnlyTheVisibleFabCapturingTouch() {
        for (x in listOf(0f, 1080f)) for (y in listOf(90f, 2290f)) {
            val p = overlayDragPlacement(safe, 3f, Offset(x, y))
            val range = overlayAnchorRange(safe, 3f)
            assertEquals(Offset(x, y.coerceIn(range.start, range.endInclusive)), p.centerOnScreen())
            assertEquals(84, p.width); assertEquals(168, p.height)
            assertTrue(p.x >= safe.left && p.y >= safe.top)
            assertTrue(p.x + p.width <= 1080 && p.y + p.height <= 2290)
            // A previous expanded host cannot keep a transparent touch rectangle behind.
            assertTrue(p.width < 296 * 3 && p.height < 356 * 3)
        }
    }
    @Test fun dockingMovesATightWindowAlongTheWholePathRatherThanReservingAnEmptyRectangle() {
        for (right in listOf(false, true)) for (position in listOf(0f, .5f, 1f)) {
            val destination = overlayPlacement(safe, 3f, right, position, false)
            for (from in listOf(Offset(0f, 90f), Offset(1080f, 2290f), Offset(540f, 1000f))) {
                val start = overlayDragPlacement(safe, 3f, from).centerOnScreen()
                for (frame in 0..30) {
                    val point = androidx.compose.ui.geometry.lerp(start, destination.centerOnScreen(), frame / 30f)
                    val host = overlayDragPlacement(safe, 3f, point)
                    assertEquals(point.x, host.centerOnScreen().x, .01f)
                    assertEquals(point.y, host.centerOnScreen().y, .01f)
                    // Outward rounding may add one pixel around a subpixel center.
                    assertTrue(host.width <= 169); assertTrue(host.height <= 169)
                    assertTrue(host.x >= safe.left && host.y >= safe.top)
                    assertTrue(host.x + host.width <= 1080 && host.y + host.height <= 2290)
                }
            }
        }
    }
}
