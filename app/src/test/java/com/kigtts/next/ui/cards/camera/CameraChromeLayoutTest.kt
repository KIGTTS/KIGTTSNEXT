package com.kigtts.next.ui.cards.camera

import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class CameraChromeLayoutTest {
    @Test fun portraitDockStaysAtTheSameScreenPositionAcrossCropsAndOverlay() {
        val (width, stage) = 380f to 640f
        val square = cameraChromeLayout(width, stage, Size(380f, 380f), false, 1f, anchorTop = 63f)
        val standard = cameraChromeLayout(width, stage, Size(380f, 507f), false, 1f, anchorTop = 63f)
        val overlay = cameraChromeLayout(width, stage + 140f, Size(380f, 780f), false, 0f, shutterInset = 140f)
        assertEquals(square.zoom, standard.zoom)
        assertEquals(square.zoom, overlay.zoom)
        assertEquals(square.photo.top, standard.photo.top, .001f)
        assertTrue(square.zoom.bottom < stage)
    }

    @Test fun landscapeDockIsFixedBesideTheShutterAndUsesTheSamePositionWithOverlay() {
        val regular = cameraChromeLayout(700f, 300f, Size(300f, 300f), true, 1f)
        val overlay = cameraChromeLayout(864f, 300f, Size(864f, 300f), true, 0f, shutterInset = 164f)
        assertEquals(regular.zoom, overlay.zoom)
        assertTrue(regular.controls.right + 8f <= regular.photo.left)
        assertTrue(regular.zoom.right < 700f)
    }

    @Test fun transparentHeaderProtectsItsTouchCornersWithoutShrinkingThePhotograph() {
        val photo = Size(360f, 360f)
        assertFalse(cameraControlsOutside(360f, 500f, photo, false, headerInset = 56f))
        val inside = cameraChromeLayout(360f, 500f, photo, false, 0f, headerInset = 56f)
        assertEquals(360f, inside.photo.width, .001f)
        assertTrue(inside.controls.top >= 56f)
        assertTrue(cameraControlsOutside(360f, 620f, photo, false, headerInset = 56f))
        val outside = cameraChromeLayout(360f, 620f, photo, false, 1f, headerInset = 56f)
        assertTrue(outside.controls.top >= 56f && outside.controls.bottom + 8f <= outside.photo.top)
    }
}
