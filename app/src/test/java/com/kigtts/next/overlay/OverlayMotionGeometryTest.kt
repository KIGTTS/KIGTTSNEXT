package com.kigtts.next.overlay

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class OverlayMotionGeometryTest {
    private val safe = OverlayBounds(0, 90, 1080, 2200)
    @Test fun draggingReachesAllFourScreenCornersWithOnlyTheVisibleFabCapturingTouch() {
        for (x in listOf(0f, 1080f)) for (y in listOf(90f, 2290f)) {
            val p = overlayDragPlacement(safe, 3f, Offset(x, y))
            assertEquals(Offset(x, y), p.centerOnScreen())
            assertEquals(84, p.width); assertEquals(84, p.height)
            assertTrue(p.x >= safe.left && p.y >= safe.top)
            assertTrue(p.x + p.width <= 1080 && p.y + p.height <= 2290)
            // A previous expanded host cannot keep a transparent touch rectangle behind.
            assertTrue(p.width < 296 * 3 && p.height < 356 * 3)
        }
    }
    @Test fun dockingKeepsBothEndpointsInsideTheBoundedSurfaceWithoutChangingTheAbsoluteAnchor() {
        for (right in listOf(false, true)) for (position in listOf(0f, .5f, 1f)) {
            val destination = overlayPlacement(safe, 3f, right, position, false)
            for (from in listOf(Offset(0f, 90f), Offset(1080f, 2290f), Offset(540f, 1000f))) {
                val host = overlayDockPlacement(safe, 3f, from, destination)
                val initial = host.local(from)
                assertEquals(from, Offset(host.x.toFloat(), host.y.toFloat()) + initial)
                assertEquals(destination.centerOnScreen(), host.centerOnScreen())
                assertTrue(initial.x in 0f..host.width.toFloat() && initial.y in 0f..host.height.toFloat())
                assertTrue(host.x >= safe.left && host.y >= safe.top)
                assertTrue(host.x + host.width <= 1080 && host.y + host.height <= 2290)
            }
        }
    }
}
