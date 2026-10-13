package com.kigtts.next.ui.cards.camera

import com.kigtts.plugin.api.PolaroidFormat
import org.junit.Assert.*
import org.junit.Test

class CameraUiPolicyTest {
    @Test fun formatButtonCyclesOnlyAvailableFormatsAndRecoversAfterOrientationChange() {
        val phone = listOf(PolaroidFormat.MINI, PolaroidFormat.SQUARE)
        assertEquals(PolaroidFormat.SQUARE, nextCameraFormat(phone, PolaroidFormat.MINI))
        assertEquals(PolaroidFormat.MINI, nextCameraFormat(phone, PolaroidFormat.SQUARE))
        assertEquals(PolaroidFormat.WIDE, nextCameraFormat(listOf(PolaroidFormat.WIDE, PolaroidFormat.SQUARE), PolaroidFormat.MINI))
        assertEquals(PolaroidFormat.MINI, nextCameraFormat(emptyList(), PolaroidFormat.MINI))
    }
    @Test fun photoWindowFitsEveryFormatWithoutPaperBorderOrAspectDistortion() {
        for (format in listOf(PolaroidFormat.MINI, PolaroidFormat.WIDE, PolaroidFormat.SQUARE)) {
            for ((width, height) in listOf(340f to 560f, 700f to 220f)) {
                val fit = cameraPhotoWindow(width, height, format.geometry.photoAspectRatio.toFloat())
                assertTrue("format=$format fit=$fit available=$width x $height", fit.width <= width && fit.height <= height)
                assertEquals(format.geometry.photoAspectRatio, (fit.width / fit.height).toDouble(), .00001)
            }
        }
        assertFalse(cameraSideControls(393f, 728f)); assertTrue(cameraSideControls(852f, 260f))
    }
    @Test fun exposureMappingIsContinuousClampedAndHasNeutralAtTheMiddle() {
        val range = -2..2
        assertEquals(2, cameraExposureAt(-40f, 100f, range))
        assertEquals(0, cameraExposureAt(50f, 100f, range))
        assertEquals(-2, cameraExposureAt(150f, 100f, range))
        assertEquals(0, cameraExposureAt(Float.NaN, 100f, range))
    }
}
