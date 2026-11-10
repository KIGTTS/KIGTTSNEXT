package com.kigtts.next.ui.cards.camera

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class CameraExposureCompensationTest {
    @Test fun focusAdjustmentAddsToTheGlobalExposureAndOnlyTheOffsetExpires() {
        val state = CameraExposureCompensation()
        state.attach(-6..9, 1f / 3f)
        state.chooseBaseline(3)
        state.chooseFocusOffset(2)
        assertEquals(5, state.index)
        assertEquals(-9..6, state.focusRange)
        state.chooseBaseline(5)
        assertEquals(7, state.index)
        state.clearFocus()
        assertEquals(5, state.index)
        assertEquals(0, state.focusOffset)
        assertEquals("+1.7", cameraEvLabel(state.baseline, state.step))
    }

    @Test fun baselineSurvivesRebindAndUsesTheNewLensStepWithoutKeepingTheFocusOffset() {
        val state = CameraExposureCompensation()
        state.attach(-6..6, .5f)
        state.chooseBaseline(-3)
        state.chooseFocusOffset(-12)
        assertEquals(-6, state.index)
        state.detach(keepBaseline = true)
        assertEquals(0, state.index)
        state.attach(-10..10, .25f)
        assertEquals(-6, state.index)
        assertEquals(0, state.focusOffset)
        state.chooseBaseline(10)
        state.chooseFocusOffset(5)
        assertEquals(10, state.index)
    }

    @Test fun luminanceHistogramHonorsCropAndIgnoresRowAndPixelPadding() {
        val buffer = ByteBuffer.wrap(byteArrayOf(0, 0, 16, 0, 24, 0, 0, 0, 8, 0, 120, 0, -1, 0, 0, 0))
        val bins = cameraLuminanceBins(buffer, 8, 2, 1, 0, 3, 2)
        assertEquals(4, bins.sum())
        for (bin in listOf(2, 3, 15, 31)) assertEquals(1, bins[bin])
        assertEquals(0, bins[0])
    }
}
