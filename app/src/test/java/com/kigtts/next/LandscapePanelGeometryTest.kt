package com.kigtts.next

import com.kigtts.next.ui.home.LandscapePanelGeometry
import org.junit.Assert.*
import org.junit.Test

class LandscapePanelGeometryTest {
    @Test fun availableWidthAdmitsOnlyCompleteColumns() {
        for (budget in 184..1000) {
            val geometry = LandscapePanelGeometry(budget.toFloat())
            assertTrue(geometry.maximum <= budget)
            assertTrue(geometry.maximumColumns == 6 || geometry.widthFor(geometry.maximumColumns + 1) > budget)
            geometry.anchors.drop(1).forEachIndexed { index, width -> assertEquals(index + 1, geometry.columnsAt(width)) }
        }
    }
    @Test fun dampingRemainsContinuousMonotonicAndReturnsExactlyToStops() {
        val geometry = LandscapePanelGeometry(700f)
        var previous = geometry.damp(64f)
        for (sample in 641..geometry.maximum.toInt() * 10) {
            val value = geometry.damp(sample / 10f)
            assertTrue(value >= previous); assertTrue(value - previous < .2f)
            previous = value
        }
        geometry.anchors.forEach { assertEquals(it, geometry.damp(it), .001f) }
        assertEquals(64f, geometry.damp(-100f), .001f)
        assertEquals(geometry.maximum, geometry.damp(5000f), .001f)
    }
    @Test fun releaseChoosesAColumnStopOrCollapseAndFlingUsesTheDragDirection() {
        val geometry = LandscapePanelGeometry(500f)
        assertEquals(316f, geometry.settle(290f, 0f), .001f)
        assertEquals(448f, geometry.settle(330f, -800f), .001f)
        assertEquals(184f, geometry.settle(300f, 800f), .001f)
        assertEquals(64f, geometry.settle(70f, 0f), .001f)
    }
}
