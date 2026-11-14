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

    @Test fun onA21By9DeviceShutterTrailingGapMatchesAfterRotation() {
        val short = 393f
        val long = short * 21f / 9f
        val portraitDepth = 140f
        val landscapeDepth = 164f
        val portrait = cameraCaptureBaseline(short, long, false, portraitDepth)
        val landscape = cameraCaptureBaseline(long, short, true, landscapeDepth)
        val portraitTail = long - portrait.dockPositionInStage() - portraitDepth
        val landscapeTail = long - landscape.dockPositionInStage() - landscapeDepth
        assertEquals(portraitTail, landscapeTail, .001f)
        // 21:9 keeps the existing portrait edge relationship on the reference screen.
        assertEquals(portrait.frame.bottom + CAMERA_PRO_BAND + CAMERA_PREVIEW_SIDE_INSET + 4f, portrait.dockStart, .001f)
        assertEquals(CAMERA_LEFT_CLEARANCE + CAMERA_PREVIEW_SIDE_INSET, landscape.frame.left, .001f)
        assertEquals(4f / 3f, landscape.frame.width / landscape.frame.height, .001f)
    }

    @Test fun shorterScreensMoveTheDockOutwardWithoutChangingLandscapeAlignment() {
        val short = 393f
        val reference = cameraDockTrailingGap(short * 21f / 9f, short)
        val compact = cameraDockTrailingGap(short * 16f / 9f, short)
        assertTrue(compact < reference)
        assertTrue(compact >= 0f)
        assertTrue(cameraDockTrailingGap(short * 2.5f, short) < reference)
        val landscape = cameraCaptureBaseline(short * 16f / 9f, short, true, 164f)
        assertEquals(CAMERA_LEFT_CLEARANCE + CAMERA_PREVIEW_SIDE_INSET, landscape.frame.left, .001f)
    }

    @Test fun squareLandscapeCentersOnTheSameFourByThreeWindow() {
        for (limit in listOf<Float?>(null, 470f, 660f)) {
            val standard = cameraCenteredCaptureWindow(900f, 420f, 4f / 3f, true, 0f, endLimit = limit)
            val square = cameraCenteredCaptureWindow(900f, 420f, 1f, true, 0f, endLimit = limit)
            assertEquals(standard.center.x, square.center.x, .001f)
            assertEquals(standard.center.y, square.center.y, .001f)
            assertEquals(square.width, square.height, .001f)
        }
    }

    @Test fun ultraLongDeviceUsesSixteenByNineShutterGapAndMovesPreviewWithinFreeSpace() {
        val width = 393f
        val compactHeight = width * 16f / 9f
        val longHeight = width * 24f / 9f
        assertEquals(cameraDockTrailingGap(compactHeight, width), cameraDockTrailingGap(longHeight, width), .001f)
        val dockExtent = 140f
        val compact = cameraCaptureBaseline(width, compactHeight, false, dockExtent)
        val long = cameraCaptureBaseline(width, longHeight, false, dockExtent)
        assertEquals(compactHeight - compact.dockPositionInStage() - dockExtent,
            longHeight - long.dockPositionInStage() - dockExtent, .001f)
        assertTrue(long.frame.top > compact.frame.top)
        assertEquals(3f / 4f, long.frame.width / long.frame.height, .001f)
        assertTrue(long.frame.bottom <= long.proPreviewLimit)
    }

    @Test fun continuousRulerProducesTactileTicksWithoutQuantizingTheParameter() {
        val span = 0f..1f
        assertEquals(cameraRulerHapticTick(.5001f, span, false), cameraRulerHapticTick(.503f, span, false))
        assertTrue(cameraRulerHapticTick(.54f, span, false) > cameraRulerHapticTick(.50f, span, false))
        assertEquals(.503f, cameraRulerPosition(.503f, span, null), 0f)
    }

    @Test fun exposureDetentOnlySnapsNearNeutral() {
        assertEquals(0f, cameraRulerPosition(.2f, -6f..6f, 0f), 0f)
        assertEquals(1.75f, cameraRulerPosition(1.75f, -6f..6f, 0f), 0f)
        assertEquals(.12f, cameraRulerPosition(.12f, -6f..6f, null), 0f)
    }
}
