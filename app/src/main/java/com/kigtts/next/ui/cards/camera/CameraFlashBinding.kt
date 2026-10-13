package com.kigtts.next.ui.cards.camera

import androidx.camera.core.ImageCapture

/** Missing hardware always means OFF; screen-flash/torch emulation is deliberately not substituted. */
internal fun applyCameraFlash(capture: ImageCapture?, requested: CameraFlash, available: Boolean): CameraFlash {
    val effective = if (available) requested else CameraFlash.OFF
    capture?.flashMode = when (effective) {
        CameraFlash.OFF -> ImageCapture.FLASH_MODE_OFF
        CameraFlash.AUTO -> ImageCapture.FLASH_MODE_AUTO
        CameraFlash.ON -> ImageCapture.FLASH_MODE_ON
    }
    return effective
}
