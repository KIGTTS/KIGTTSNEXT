package com.kigtts.next.ui.cards.camera

import org.junit.Assert.*
import org.junit.Test

class CameraCaptureBaselineTest {
    @Test fun normalZoomSitsBelowFourByThreeAndMovesUnderPreviewOnlyInTightWindows() {
        val tall = cameraCaptureBaseline(393f, 900f, false, 140f)
        assertEquals(3f / 4f, tall.frame.width / tall.frame.height, .001f)
        assertTrue(tall.zoom(false).top > tall.frame.bottom)
        assertTrue(tall.zoom(false).bottom < tall.dockStart)
        val short = cameraCaptureBaseline(393f, 655f, false, 140f)
        assertTrue(short.zoom(false).top < short.frame.bottom)
    }

    @Test fun professionalStripReplacesNormalZoomSlotBelowPreviewWithoutShiftingShutter() {
        val tall = cameraCaptureBaseline(393f, 900f, false, 140f)
        val photo = cameraCenteredCaptureWindow(393f, 892f, 3f / 4f, false, 0f, endLimit = tall.proPreviewLimit)
        assertEquals(tall.frame, photo)
        assertEquals(CAMERA_PREVIEW_SIDE_INSET, tall.pro().top - photo.bottom, .001f)
        assertTrue(tall.pro().bottom < tall.dockStart)
        assertEquals(tall.zoom(false).center.y, tall.pro().center.y, .001f)
        assertEquals(CAMERA_RAIL_GAP, tall.pro().top - tall.zoom(true).bottom, .001f)
        assertEquals(tall.frame.width, tall.pro().width, .001f)
        val landscape = cameraCaptureBaseline(760f, 450f, true, 164f)
        val frame = landscape.frame
        assertTrue(frame.left >= CAMERA_LEFT_CLEARANCE)
        assertTrue(frame.top < CAMERA_HEADER_CLEARANCE)
        assertEquals(442f - frame.bottom, frame.top, .001f)
        assertEquals(4f / 3f, frame.width / frame.height, .001f)
        assertEquals(CAMERA_RAIL_GAP, landscape.pro().left - landscape.zoom(true).right, .001f)
        assertEquals(frame.height, landscape.pro().height, .001f)
        val proPhoto = cameraCenteredCaptureWindow(760f, 442f, 4f / 3f, true, 0f, endLimit = landscape.proPreviewLimit)
        assertEquals(CAMERA_PREVIEW_SIDE_INSET, landscape.pro().left - proPhoto.right, .001f)
        assertEquals(landscape.zoom(false).center.x, landscape.pro().center.x, .001f)
        val full = cameraCenteredCaptureWindow(760f, 442f, 760f / 442f, true, 1f)
        assertTrue(full.left < frame.left)
        assertEquals(760f / 442f, full.width / full.height, .001f)
    }

    @Test fun verySmallWindowDoesNotProduceNegativeOrOverlappingControlBands() {
        val short = cameraCaptureBaseline(393f, 655f, false, 140f)
        val photo = cameraCenteredCaptureWindow(393f, 647f, 3f / 4f, false, 0f, endLimit = short.proPreviewLimit)
        assertTrue(photo.bottom < short.pro().top)
        assertEquals(3f / 4f, photo.width / photo.height, .001f)
        assertTrue(short.pro().top >= 0)
        assertTrue(short.zoom(true).bottom <= short.pro().top)
        assertTrue(short.pro().bottom <= short.dockStart)
    }

    @Test fun viewfinderGapsMatchSideInsetsAndRotateWithTheControls() {
        val portrait = cameraCenteredCaptureWindow(390f, 852f, .75f, false, 0f)
        assertEquals(CAMERA_PREVIEW_SIDE_INSET, portrait.left, .001f)
        assertEquals(portrait.left, portrait.top - CAMERA_HEADER_CLEARANCE, .001f)
        assertEquals(portrait.left, 390f - portrait.right, .001f)
        val landscape = cameraCenteredCaptureWindow(852f, 390f, 4f / 3f, true, 0f)
        assertEquals(CAMERA_PREVIEW_SIDE_INSET, landscape.top, .001f)
        assertEquals(landscape.top, 390f - landscape.bottom, .001f)
        val full = cameraCenteredCaptureWindow(390f, 852f, 390f / 852f, false, 1f)
        assertEquals(0f, full.top, .001f)
    }

    @Test fun exposureDetentOnlySnapsNearNeutral() {
        assertEquals(0f, cameraRulerPosition(.2f, -6f..6f, 0f), 0f)
        assertEquals(1.75f, cameraRulerPosition(1.75f, -6f..6f, 0f), 0f)
        assertEquals(.12f, cameraRulerPosition(.12f, -6f..6f, null), 0f)
    }
}
