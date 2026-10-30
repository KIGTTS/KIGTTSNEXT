package com.kigtts.next.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlayFrameUpdatesTest {
    @Test fun resizeFlushesTheLatestGeometryImmediatelyAndItsQueuedFrameDoesNotRepeatIt() {
        val frames = mutableListOf<Runnable>()
        var geometry = 1
        val applied = mutableListOf<Int>()
        val updates = OverlayFrameUpdates({ frames += it }) { applied += geometry }
        updates.request()
        geometry = 2
        updates.flush()
        assertEquals(listOf(2), applied)
        frames.removeAt(0).run()
        assertEquals(listOf(2), applied)
        geometry = 3; updates.request(); frames.removeAt(0).run()
        assertEquals(listOf(2, 3), applied)
    }
    @Test fun aResizedSurfaceAtTheTemporaryScreenOriginMustNotPassTheFirstDrawGate() {
        val gate = OverlayResizeGate()
        gate.resizing()
        assertFalse(gate.canDraw(sizeMatches = true, positionMatches = false))
        assertFalse(gate.canDraw(sizeMatches = true, positionMatches = false))
        assertTrue(gate.canDraw(sizeMatches = true, positionMatches = true))
        gate.resizing()
        gate.resizing(continuous = true)
        assertTrue(gate.canDraw(sizeMatches = false, positionMatches = false))
    }
    @Test fun aBurstOfPointerUpdatesAppliesOnlyTheLatestPositionPerDisplayFrame() {
        val frames = mutableListOf<Runnable>()
        var pointer = 0
        val drawn = mutableListOf<Int>()
        val updates = OverlayFrameUpdates({ frames += it }) { drawn += pointer }
        repeat(20) { pointer = it; updates.request() }
        assertEquals(1, frames.size)
        frames.removeAt(0).run()
        assertEquals(listOf(19), drawn)
        pointer = 37; updates.request(); frames.removeAt(0).run()
        assertEquals(listOf(19, 37), drawn)
        updates.request(); updates.close(); frames.removeAt(0).run()
        updates.request()
        assertEquals(listOf(19, 37), drawn)
        assertTrue(frames.isEmpty())
    }
    @Test fun continuousRetractionDoesNotSuspendEveryFrameOfTheAnimation() {
        val gate = OverlayResizeGate()
        repeat(40) {
            gate.resizing(continuous = true)
            assertTrue(gate.canDraw(false))
        }
        gate.resizing()
        assertFalse(gate.canDraw(false))
        assertTrue(gate.canDraw(true))
    }
    @Test fun movingAWindowKeepsItsLocalMenuGeometryStable() {
        val compact = overlayPlacement(OverlayBounds(0, 32, 1080, 2200), 3f, true, 0f, false)
        assertTrue(compact.sameLocalGeometry(compact.copy(x = 20, y = 1200)))
        assertFalse(compact.sameLocalGeometry(compact.copy(fabX = compact.fabX / 2)))
        assertFalse(compact.sameLocalGeometry(compact.copy(width = compact.width / 2)))
    }
}
