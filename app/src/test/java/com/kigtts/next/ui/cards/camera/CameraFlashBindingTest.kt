package com.kigtts.next.ui.cards.camera

import androidx.camera.core.ImageCapture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraFlashBindingTest {
    @Test fun everyModeUpdatesTheRealImageCaptureFlashProperty() {
        val capture = ImageCapture.Builder().build()
        assertEquals(CameraFlash.ON, applyCameraFlash(capture, CameraFlash.ON, true))
        assertEquals(ImageCapture.FLASH_MODE_ON, capture.flashMode)
        assertEquals(CameraFlash.AUTO, applyCameraFlash(capture, CameraFlash.AUTO, true))
        assertEquals(ImageCapture.FLASH_MODE_AUTO, capture.flashMode)
        assertEquals(CameraFlash.OFF, applyCameraFlash(capture, CameraFlash.OFF, true))
        assertEquals(ImageCapture.FLASH_MODE_OFF, capture.flashMode)
    }

    @Test fun aLensWithoutAFlashUnitClearsPreviouslySelectedOnAndAutoModes() {
        val capture = ImageCapture.Builder().build()
        applyCameraFlash(capture, CameraFlash.ON, true)
        assertEquals(CameraFlash.OFF, applyCameraFlash(capture, CameraFlash.AUTO, false))
        assertEquals(ImageCapture.FLASH_MODE_OFF, capture.flashMode)
        assertEquals(CameraFlash.OFF, applyCameraFlash(capture, CameraFlash.ON, false))
        assertEquals(ImageCapture.FLASH_MODE_OFF, capture.flashMode)
    }

    @Test fun requestedModeCanBeAppliedAfterTheLensBecomesAvailable() {
        assertEquals(CameraFlash.OFF, applyCameraFlash(null, CameraFlash.AUTO, false))
        val capture = ImageCapture.Builder().build()
        assertEquals(CameraFlash.AUTO, applyCameraFlash(capture, CameraFlash.AUTO, true))
        assertEquals(ImageCapture.FLASH_MODE_AUTO, capture.flashMode)
    }
}
