package com.kigtts.next.ui.cards.camera

import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class CameraChromeLayoutTest {
    @Test fun portraitUsesActualIndependentTopAndBottomSpaceIncludingSafetyMargins() {
        val photo = Size(380f, 380f)
        assertEquals(CameraChromeSpace(false, false), cameraChromeSpace(380f, 498f, photo, false))
        assertEquals(CameraChromeSpace(false, true), cameraChromeSpace(380f, 508f, photo, false))
        assertEquals(CameraChromeSpace(true, true), cameraChromeSpace(380f, 516f, photo, false))
        val layout = cameraChromeLayout(380f, 516f, photo, false, 1f, 1f, CameraChromeSpace(true, true))
        assertTrue(layout.controls.bottom + 8f <= layout.photo.top)
        assertTrue(layout.zoom.top >= layout.photo.bottom + 8f)
        assertTrue(layout.controls.top >= 4f && layout.zoom.bottom <= 512f)
    }

    @Test fun landscapeLeavesTheViewportUnchangedAndPlacesBothRailsInTheSideGaps() {
        val photo = Size(300f, 300f)
        val room = cameraChromeSpace(700f, 300f, photo, true)
        val layout = cameraChromeLayout(700f, 300f, photo, true, 1f, 1f, room)
        assertEquals(CameraChromeSpace(true, true), room)
        assertEquals(300f, layout.photo.width, .001f); assertEquals(300f, layout.photo.height, .001f)
        assertTrue(layout.controls.right + 8f <= layout.photo.left)
        assertTrue(layout.zoom.left >= layout.photo.right + 8f)
        assertTrue(layout.controls.left >= 4f && layout.zoom.right <= 696f)
        val narrow = cameraChromeSpace(400f, 300f, photo, true)
        assertEquals(CameraChromeSpace(false, false), narrow)
        val inside = cameraChromeLayout(400f, 300f, photo, true, 0f, 0f, narrow)
        assertTrue(inside.controls.left >= inside.photo.left && inside.controls.right <= inside.photo.right)
        assertTrue(inside.zoom.left >= inside.photo.left && inside.zoom.right <= inside.photo.right)
    }

    @Test fun continuousPlacementStaysInTheStageWhileThePhotoItselfChangesRatio() {
        val width = 360f; val height = 500f
        val space = CameraChromeSpace(true, true)
        for (step in 0..20) {
            val progress = step / 20f
            val photo = cameraPhotoWindow(width, height, .742f + (1f - .742f) * progress)
            val layout = cameraChromeLayout(width, height, photo, false, progress, progress, space)
            for (bounds in listOf(layout.controls, layout.zoom)) {
                assertTrue(bounds.left >= 0 && bounds.right <= width)
                assertTrue(bounds.top >= 0 && bounds.bottom <= height)
            }
            assertEquals(photo.width, layout.photo.width, .001f)
            assertEquals(photo.height, layout.photo.height, .001f)
        }
    }

    @Test fun transparentHeaderProtectsItsTouchCornersWithoutShrinkingThePhotograph() {
        val photo = Size(360f, 360f)
        val compact = cameraChromeSpace(360f, 500f, photo, false, headerInset = 56f)
        assertEquals(CameraChromeSpace(false, true), compact)
        val inside = cameraChromeLayout(360f, 500f, photo, false, 0f, 1f, compact, headerInset = 56f)
        assertEquals(360f, inside.photo.width, .001f); assertEquals(360f, inside.photo.height, .001f)
        assertTrue(inside.controls.top >= 56f)
        val room = cameraChromeSpace(360f, 620f, photo, false, headerInset = 56f)
        assertTrue(room.controlsOutside)
        val outside = cameraChromeLayout(360f, 620f, photo, false, 1f, 1f, room, headerInset = 56f)
        assertTrue(outside.controls.top >= 56f && outside.controls.bottom + 8f <= outside.photo.top)
        val horizontal = cameraChromeLayout(580f, 300f, Size(480f, 300f), true, 1f, 1f,
            CameraChromeSpace(false, false), headerInset = 56f)
        assertTrue(horizontal.controls.top >= 56f)
    }
}
