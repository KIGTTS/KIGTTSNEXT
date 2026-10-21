package com.kigtts.next.ui.cards.camera

import android.view.Surface
import org.junit.Assert.*
import org.junit.Test

class CameraLevelMathTest {
    @Test fun uprightGravityIsLevelInEveryDisplayRotation() {
        val readings = listOf(Triple(0f, 9.8f, Surface.ROTATION_0), Triple(-9.8f, 0f, Surface.ROTATION_90),
            Triple(0f, -9.8f, Surface.ROTATION_180), Triple(9.8f, 0f, Surface.ROTATION_270))
        for ((x, y, rotation) in readings) {
            val value = requireNotNull(cameraLevelReading(x, y, 0f, rotation))
            assertTrue(value.aligned); assertFalse(value.nearFlat); assertEquals(0f, value.rollDegrees, .001f)
        }
    }
    @Test fun tiltPreservesDirectionAcrossPortraitAndLandscape() {
        val portrait = requireNotNull(cameraLevelReading(1.7f, 9.6f, 0f, Surface.ROTATION_0))
        val landscape = requireNotNull(cameraLevelReading(-9.6f, 1.7f, 0f, Surface.ROTATION_90))
        assertEquals(10f, portrait.rollDegrees, .1f)
        assertEquals(portrait.rollDegrees, landscape.rollDegrees, .001f)
        assertFalse(portrait.aligned)
    }
    @Test fun flatCameraUsesABullseyeInsteadOfAnUnstableHorizon() {
        val level = requireNotNull(cameraLevelReading(0f, 0f, 9.8f, Surface.ROTATION_0))
        assertTrue(level.nearFlat); assertTrue(level.aligned)
        val tilted = requireNotNull(cameraLevelReading(1f, 0f, 9.7f, Surface.ROTATION_0))
        assertTrue(tilted.nearFlat); assertFalse(tilted.aligned); assertTrue(tilted.flatX > 0f)
    }
    @Test fun invalidOrEmptySensorReadingsDoNotShowAFalseLevel() {
        for (xyz in listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(Float.NaN, 0f, 9.8f),
            floatArrayOf(Float.POSITIVE_INFINITY, 0f, 9.8f), floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, 0f)))
            assertNull(cameraLevelReading(xyz[0], xyz[1], xyz[2], Surface.ROTATION_0))
    }
    @Test fun oppositeLandscapeGripsAndUpsideDownGravityStillHighlightAHorizontalLine() {
        for (rotation in listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)) {
            val gravity = if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) 9.8f to 0f else 0f to 9.8f
            for (direction in listOf(-1f, 1f)) {
                val reading = requireNotNull(cameraLevelReading(gravity.first * direction, gravity.second * direction, 0f, rotation))
                assertTrue("rotation=$rotation direction=$direction $reading", reading.aligned)
                assertEquals(0f, reading.rollDegrees, .001f)
            }
        }
        assertFalse(requireNotNull(cameraLevelReading(9.8f, 0f, 0f, Surface.ROTATION_0)).aligned)
        assertFalse(requireNotNull(cameraLevelReading(0f, 9.8f, 0f, Surface.ROTATION_90)).aligned)
    }
}
