package com.kigtts.next.ui.cards.camera

import androidx.camera.extensions.ExtensionMode
import org.junit.Assert.*
import org.junit.Test

class CameraProcessingTest {
    @Test fun ordinaryCaptureNeverForcesAnAvailableNightOrHdrExtension() {
        assertEquals(listOf<Int?>(null), cameraExtensionPlan(CameraProcessing.STANDARD, true))
        assertEquals(listOf<Int?>(null), cameraExtensionPlan(CameraProcessing.STANDARD, false))
    }
    @Test fun requestedVendorModeFallsBackToStandardWithoutTryingAnUnrelatedMode() {
        val expected = mapOf(CameraProcessing.AUTO to ExtensionMode.AUTO, CameraProcessing.HDR to ExtensionMode.HDR,
            CameraProcessing.NIGHT to ExtensionMode.NIGHT)
        expected.forEach { (choice, mode) ->
            assertEquals(listOf(mode, null), cameraExtensionPlan(choice, true))
            assertEquals(listOf<Int?>(null), cameraExtensionPlan(choice, false))
        }
    }
    @Test fun nativeZoomStopsRespectCameraReportedHardwareBounds() {
        assertEquals(listOf(1f, 2f), cameraZoomStops(requireNotNull(cameraZoomRange(1f, 4f))))
        assertEquals(listOf(1.2f, 2f), cameraZoomStops(requireNotNull(cameraZoomRange(1.2f, 3f))))
        assertNull(cameraZoomRange(1f, 1f)); assertNull(cameraZoomRange(Float.NaN, 4f))
        assertNull(cameraZoomRange(2f, 1f)); assertNull(cameraZoomRange(.6f, Float.POSITIVE_INFINITY))
    }
}
