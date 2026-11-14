package com.kigtts.next.ui.cards.camera

import org.junit.Assert.*
import org.junit.Test

class CameraCaptureBaselineTest {
    @Test fun portraitDockOnlyOverlapsWhenThereIsNotEnoughSpace() {
        val tall = cameraCaptureBaseline(393f, 900f, false, 140f)
        assertEquals(tall.frame.bottom, tall.dockStart, .001f)
        assertEquals(9f / 16f, tall.frame.width / tall.frame.height, .001f)
        val short = cameraCaptureBaseline(393f, 655f, false, 140f)
        assertTrue(short.dockStart < short.frame.bottom)
        assertTrue(short.pro().bottom < short.dockStart)
        assertTrue(short.zoom(true).bottom <= short.pro().top)
        assertEquals(short.frame.width, short.pro().width, .001f)
    }

    @Test fun landscapeReservesLeftRatherThanTopAndFitsProfessionalBandToTheFrame() {
        val landscape = cameraCaptureBaseline(760f, 450f, true, 164f)
        val frame = landscape.frame
        assertEquals(CAMERA_LEFT_CLEARANCE, frame.left, .001f)
        // Any top gap is centered letterboxing, not a header inset.
        assertTrue(frame.top < CAMERA_HEADER_CLEARANCE)
        assertEquals(442f - frame.bottom, frame.top, .001f)
        assertEquals(16f / 9f, frame.width / frame.height, .001f)
        assertTrue(landscape.zoom(true).right <= landscape.pro().left)
        assertEquals(frame.height, landscape.pro().height, .001f)
        assertEquals(0f, cameraCaptureBaseline(1100f, 450f, true, 164f).frame.top, .001f)
        val full = cameraCenteredCaptureWindow(760f, 442f, 760f / 442f, true, 1f)
        assertTrue(full.left < frame.left)
        assertEquals(760f / 442f, full.width / full.height, .001f)
    }

    @Test fun verySmallWindowDoesNotProduceNegativeOrOverlappingControlBands() {
        val short = cameraCaptureBaseline(230f, 190f, false, 140f)
        assertTrue(short.pro().top >= 0)
        assertTrue(short.zoom(true).bottom <= short.pro().top)
        assertTrue(short.pro().bottom <= short.dockStart)
    }
}
