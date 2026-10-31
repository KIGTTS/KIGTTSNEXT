package com.kigtts.next.ui.cards.camera

import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class CameraZoomDialGeometryTest {
    @Test fun portraitAndLandscapeExposeTheSameShallowArcInsideThePhoto() {
        val portrait = cameraZoomDialGeometry(Size(360f, 480f), false)
        val landscape = cameraZoomDialGeometry(Size(480f, 360f), true)
        assertEquals(portrait.radius, landscape.radius, .01f)
        assertEquals(180f, portrait.center.x, .01f)
        assertEquals(180f, landscape.center.y, .01f)
        val bottomDepth = 480f - (portrait.center.y - portrait.radius)
        val rightDepth = 480f - (landscape.center.x - landscape.radius)
        assertTrue(bottomDepth / 480f in .20f.. .25f)
        assertEquals(bottomDepth, rightDepth, .01f)
    }

    @Test fun shortCropsStillLeaveMostOfThePhotoClear() {
        for (size in listOf(Size(360f, 360f), Size(360f, 200f), Size(200f, 360f))) {
            for (vertical in listOf(false, true)) {
                val dial = cameraZoomDialGeometry(size, vertical)
                val depth = if (vertical) size.width else size.height
                assertTrue(dial.radius * .5f <= depth * .32f + .01f)
            }
        }
    }
}
