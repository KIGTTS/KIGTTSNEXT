package com.kigtts.next.ui.cards.camera

import android.view.Surface
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class CameraViewportTest {
    @Test fun errorControlsChangingPreviewHeightDoNotRestartTheSameCrop() {
        val miniRatio = 46.0 / 62.0
        val baseline = cameraViewportKey(IntSize(230, 310), miniRatio, Surface.ROTATION_0)
        // A MINI paper constrained by height gets smaller when the retry controls appear.
        for (height in 150..720) {
            val resized = IntSize((height * miniRatio).roundToInt(), height)
            assertEquals("Unexpected camera restart at $resized", baseline,
                cameraViewportKey(resized, miniRatio, Surface.ROTATION_0))
        }
    }
    @Test fun bindingWaitsForBothDimensionsAndForTheRequestedCrop() {
        val pending = cameraViewportKey(IntSize.Zero, 1.0, Surface.ROTATION_0)
        assertFalse(pending.layoutReady); assertFalse(pending.matchesPhotoRatio)
        assertFalse(cameraViewportKey(IntSize(100, 0), 1.0, Surface.ROTATION_0).layoutReady)
        assertFalse(cameraViewportKey(IntSize(0, 100), 1.0, Surface.ROTATION_0).layoutReady)
        val mismatch = cameraViewportKey(IntSize(300, 100), 1.0, Surface.ROTATION_0)
        assertTrue(mismatch.layoutReady); assertFalse(mismatch.matchesPhotoRatio)
        val ready = cameraViewportKey(IntSize(300, 300), 1.0, Surface.ROTATION_0)
        assertTrue(ready.layoutReady); assertTrue(ready.matchesPhotoRatio)
        assertNotEquals(pending, ready); assertNotEquals(mismatch, ready)
    }
    @Test fun halfTurnRebindsEvenWhenTheLayoutDoesNotChange() {
        val size = IntSize(460, 620)
        val portrait = cameraViewportKey(size, 46.0 / 62.0, Surface.ROTATION_0)
        val inverted = cameraViewportKey(size, 46.0 / 62.0, Surface.ROTATION_180)
        assertNotEquals(portrait, inverted)
        assertEquals(inverted, cameraViewportKey(size, 46.0 / 62.0, Surface.ROTATION_180))
    }
}
